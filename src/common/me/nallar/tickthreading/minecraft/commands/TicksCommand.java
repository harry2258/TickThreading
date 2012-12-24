package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class TicksCommand extends Command {
	public static String name = "ticks";

	@Override
	public String getCommandName() {
		return name;
	}

	public boolean canCommandSenderUseCommand(ICommandSender par1ICommandSender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		if (commandSender instanceof Entity) {
			World world = ((Entity) commandSender).worldObj;
			if (arguments.contains("all")) {

			} else {
				sendChat(commandSender, TickThreading.instance().getManager(world).getDetailedStats());
			}
		}
	}
}
