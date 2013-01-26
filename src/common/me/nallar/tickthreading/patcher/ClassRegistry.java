package me.nallar.tickthreading.patcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import javassist.CannotCompileException;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.MethodInfo;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.CollectionsUtil;
import me.nallar.tickthreading.util.EnumerableWrapper;
import me.nallar.tickthreading.util.LocationUtil;

public class ClassRegistry {
	private static final String hashFileName = "TickThreading.hash";
	private final Map<String, File> classNameToLocation = new HashMap<String, File>();
	private final Map<File, Integer> locationToPatchHash = new HashMap<File, Integer>();
	private final Map<File, Integer> expectedPatchHashes = new HashMap<File, Integer>();
	private final Map<File, Map<String, byte[]>> additionalClasses = new HashMap<File, Map<String, byte[]>>();
	private final Set<File> loadedFiles = new HashSet<File>();
	private final Set<File> updatedFiles = new HashSet<File>();
	private final Map<String, Set<File>> unsafeClassNames = new HashMap<String, Set<File>>();
	private final Set<ClassPath> classPathSet = new HashSet<ClassPath>();
	private final Map<String, byte[]> replacementFiles = new HashMap<String, byte[]>();
	private final Map<String, Set<File>> packageLocations = new HashMap<String, Set<File>>();
	public final ClassPool classes = new ClassPool(false);
	public boolean disableJavassistLoading = false;
	public boolean forcePatching = false;

	{
		MethodInfo.doPreverify = true;
		classes.appendSystemPath();
	}

	public void clearClassInfo() {
		finishModifications();
		classNameToLocation.clear();
		additionalClasses.clear();
		updatedFiles.clear();
		unsafeClassNames.clear();
		replacementFiles.clear();
		loadedFiles.clear();
		packageLocations.clear();
	}

	public void loadFiles(Iterable<File> filesToLoad) throws IOException {
		for (File file : filesToLoad) {
			String extension = file.getName().toLowerCase();
			extension = extension.substring(extension.lastIndexOf('.') + 1);
			try {
				if (file.isDirectory()) {
					if (!".disabled".equals(file.getName())) {
						loadFiles(Arrays.asList(file.listFiles()));
					}
				} else if ("jar".equals(extension)) {
					loadJar(new JarFile(file));
				} else if ("zip".equals(extension) || "litemod".equals(extension)) {
					loadZip(new ZipFile(file));
				}
			} catch (ZipException e) {
				throw new ZipException(e.getMessage() + " file: " + file);
			}
		}
	}

	void loadJar(JarFile jar) throws IOException {
		loadZip(jar);
	}

	void appendClassPath(String path) throws NotFoundException {
		if (!disableJavassistLoading) {
			classPathSet.add(classes.appendClassPath(path));
		}
	}

	void loadZip(ZipFile zip) throws IOException {
		File file = new File(zip.getName());
		if (!loadedFiles.add(file)) {
			return;
		}
		try {
			appendClassPath(file.getAbsolutePath());
		} catch (Exception e) {
			Log.severe("Javassist could not load " + file, e);
		}
		for (ZipEntry zipEntry : new EnumerableWrapper<ZipEntry>((Enumeration<ZipEntry>) zip.entries())) {
			String name = zipEntry.getName();
			if (name.endsWith(".class")) {
				String className = name.replace('/', '.').substring(0, name.lastIndexOf('.'));
				String packageName = getPackage(className);
				Set<File> packageLocation = packageLocations.get(packageName);
				if (packageLocation == null) {
					packageLocation = new HashSet<File>();
					packageLocations.put(packageName, packageLocation);
				}
				packageLocation.add(file);
				if (classNameToLocation.containsKey(className)) {
					Set<File> locations = unsafeClassNames.get(className);
					if (locations == null) {
						locations = new HashSet<File>();
						locations.add(classNameToLocation.get(className));
						unsafeClassNames.put(className, locations);
					}
					locations.add(file);
				} else {
					classNameToLocation.put(className, file);
				}
			} else if (name.equals(hashFileName)) {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ByteStreams.copy(zip.getInputStream(zipEntry), output);
				int hash = Integer.valueOf(new String(output.toByteArray(), "UTF-8"));
				locationToPatchHash.put(file, hash);
			}
		}
		zip.close();
	}

	public void update(String className, byte[] replacement) {
		if (unsafeClassNames.containsKey(className)) {
			Log.warning(className + " is in multiple jars: " + CollectionsUtil.join(unsafeClassNames.get(className), ", "));
		}
		String packageName = getPackage(className);
		File location = classNameToLocation.get(className);
		if (packageLocations.containsKey(packageName)) {
			for (Map.Entry<String, Set<File>> entry : packageLocations.entrySet()) {
				if (entry.getValue().contains(location)) {
					updatedFiles.addAll(packageLocations.get(entry.getKey()));
					if (packageLocations.get(entry.getKey()).contains(LocationUtil.locationOf(PatchMain.class))) {
						Log.severe(packageName + " -> " + entry.getKey() + " tried to add TT jar to updated list!");
					}
				}
			}
			updatedFiles.addAll(packageLocations.get(packageName));
		}
		updatedFiles.add(classNameToLocation.get(className));
		replacementFiles.put(className.replace('.', '/') + ".class", replacement);
	}

	Map<String, byte[]> getAdditionalClasses(File file) {
		Map<String, byte[]> additionalClasses = this.additionalClasses.get(file);
		if (additionalClasses == null) {
			additionalClasses = new HashMap<String, byte[]>();
			this.additionalClasses.put(file, additionalClasses);
		}
		return additionalClasses;
	}

	public void add(Object requires, String additionalClassName) throws NotFoundException, IOException, CannotCompileException {
		if (additionalClassName.startsWith("java.")) {
			return;
		}
		String requiringClassName = null;
		if (requires instanceof CtBehavior) {
			requiringClassName = ((CtBehavior) requires).getDeclaringClass().getName();
		} else if (requires instanceof CtClass) {
			requiringClassName = ((CtClass) requires).getName();
		}
		if (requiringClassName == null) {
			Log.severe("Can't add " + additionalClassName + " as a class required by unknown type: " + requires.getClass().getCanonicalName());
			return;
		}
		File location = classNameToLocation.get(requiringClassName);
		add(location, additionalClassName, getClass(additionalClassName).toBytecode());
	}

	void add(File file, String className, byte[] byteCode) {
		getAdditionalClasses(file).put(className.replace('.', '/') + ".class", byteCode);
	}

	public File getLocation(String className) {
		return classNameToLocation.get(className);
	}

	public void finishModifications() {
		for (ClassPath classPath : classPathSet) {
			classes.removeClassPath(classPath);
		}
		classPathSet.clear();
	}

	private static File makeTempFile(File tempLocation, File file) {
		return new File(tempLocation, file.getName() + (String.valueOf(Math.random())).replace('.', '_') + ".tmp");
	}

	private static void delete(File f) {
		if (f.isDirectory()) {
			for (File c : f.listFiles()) {
				delete(c);
			}
		}
		f.delete();
	}

	public void save(File backupDirectory) throws IOException {
		finishModifications();
		File tempFile = null, renameFile = null;
		File tempDirectory = new File(backupDirectory, "temp");
		tempDirectory.mkdir();
		ZipInputStream zin = null;
		ZipOutputStream zout = null;
		backupDirectory.mkdir();
		updatedFiles.remove(LocationUtil.locationOf(PatchMain.class));
		try {
			for (File zipFile : updatedFiles) {
				File backupFile = new File(backupDirectory, zipFile.getName());
				backupFile.delete();
				Files.copy(zipFile, backupFile);
				tempFile = makeTempFile(tempDirectory, zipFile);
				tempFile.delete();
				if (zipFile.renameTo(tempFile)) {
					renameFile = zipFile;
					tempFile.deleteOnExit();
				} else {
					throw new IOException("Couldn't rename " + zipFile + " -> " + tempFile);
				}
				zin = new ZipInputStream(new FileInputStream(tempFile));
				zout = new ZipOutputStream(new FileOutputStream(zipFile));
				Set<String> replacements = new HashSet<String>();
				Map<String, byte[]> additionalClasses = getAdditionalClasses(zipFile);
				ZipEntry zipEntry;
				while ((zipEntry = zin.getNextEntry()) != null) {
					String entryName = zipEntry.getName();
					if (entryName.equals(hashFileName) || additionalClasses.containsKey(entryName) || (entryName.startsWith("META-INF/") && !(entryName.length() > 0 && entryName.charAt(entryName.length() - 1) == '/') && !entryName.toUpperCase().endsWith("MANIFEST.MF")) && (entryName.length() - entryName.replace("/", "").length() == 1)) {
						// Skip
					} else if (replacementFiles.containsKey(entryName)) {
						replacements.add(entryName);
					} else {
						zout.putNextEntry(new ZipEntry(entryName));
						ByteStreams.copy(zin, zout);
					}
				}
				for (String name : replacements) {
					zout.putNextEntry(new ZipEntry(name));
					zout.write(replacementFiles.get(name));
					zout.closeEntry();
				}
				for (Map.Entry<String, byte[]> stringEntry : additionalClasses.entrySet()) {
					zout.putNextEntry(new ZipEntry(stringEntry.getKey()));
					zout.write(stringEntry.getValue());
					zout.closeEntry();
				}
				if (expectedPatchHashes.containsKey(zipFile)) {
					zout.putNextEntry(new ZipEntry(hashFileName));
					String patchHash = String.valueOf(expectedPatchHashes.get(zipFile));
					zout.write(patchHash.getBytes("UTF-8"));
					Log.info("Patched " + replacements.size() + " classes in " + zipFile.getName() + ", patchHash: " + patchHash);
				} else {
					Log.info("Removed signing info from " + zipFile.getName());
				}
				if (!additionalClasses.isEmpty()) {
					Log.info("Added " + additionalClasses.size() + " classes required by patches.");
				}
				zin.close();
				zout.close();
				tempFile.delete();
				renameFile = null;
			}
		} catch (ZipException e) {
			if (zin != null) {
				zin.close();
			}
			if (zout != null) {
				zout.close();
			}
			if (renameFile != null) {
				tempFile.renameTo(renameFile);
			}
			throw e;
		} finally {
			delete(tempDirectory);
		}
	}

	public CtClass getClass(String className) throws NotFoundException {
		return classes.get(className);
	}

	public void loadPatchHashes(PatchManager patchManager) {
		Map<String, Integer> patchHashes = patchManager.getHashes();
		for (Map.Entry<String, Integer> stringIntegerEntry : patchHashes.entrySet()) {
			File location = classNameToLocation.get(stringIntegerEntry.getKey());
			if (location == null) {
				continue;
			}
			int hash = stringIntegerEntry.getValue();
			Integer currentHash = expectedPatchHashes.get(location);
			expectedPatchHashes.put(location, (currentHash == null) ? hash : currentHash * 31 + hash);
		}
	}

	public boolean shouldPatch(String className) {
		return shouldPatch(classNameToLocation.get(className));
	}

	boolean shouldPatch(File file) {
		return forcePatching || file == null || !(expectedPatchHashes.get(file).equals(locationToPatchHash.get(file)));
	}

	public boolean shouldPatch() {
		for (File file : expectedPatchHashes.keySet()) {
			if (shouldPatch(file)) {
				return true;
			}
		}
		return false;
	}

	public void restoreBackups(File backupDirectory) {
		for (Map.Entry<File, Integer> fileIntegerEntry : locationToPatchHash.entrySet()) {
			Integer expectedHash = expectedPatchHashes.get(fileIntegerEntry.getKey());
			Integer actualHash = fileIntegerEntry.getValue();
			if (actualHash == null) {
				continue;
			}
			if (forcePatching || !actualHash.equals(expectedHash)) {
				fileIntegerEntry.getKey().delete();
				try {
					Files.copy(new File(backupDirectory, fileIntegerEntry.getKey().getName()), fileIntegerEntry.getKey());
				} catch (IOException e) {
					Log.severe("Failed to restore unpatched backup before patching.");
				}
			}
		}
	}

	private static String getPackage(String className) {
		return className.substring(0, className.contains(".") ? className.lastIndexOf('.') : 0);
	}
}
