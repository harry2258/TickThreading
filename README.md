TickThreading [![Build Status](https://jenkins.nallar.me/job/TickThreading/branch/1.10.2/badge/icon)](https://jenkins.nallar.me/job/TickThreading/branch/1.10.2/)
==========
Multi-threaded minecraft. Requires Forge.

Update for 1.10 in-progress.

Copyright &copy; 2012-2016, nallar <ross@nallar.me>

TickThreading is licensed under the MIT license

Download
-----

Download the latest builds from [Jenkins].

Compatibility with other mods
-----
[See the wiki.](https://github.com/nallar/TickThreading/wiki/Mod-Compatibility)

Configuration
-----
TickThreading uses minecraft forge's suggested config location - minecraft folder/configs/TickThreading.cfg
Some additional configuration options which need to be set before the server is started can be changed in the ttlaunch.properties file in your server folder.
It's commented quite well, and is hopefully understandable. If any of the descriptions don't make sense please make an issue.

Logging
-----
TickThreading stores its logs in the TickThreadingLogs directory, and will keep the previous 5 logs.
Make sure to include all relevant logs if you run into a problem.

Compiling
---------
TickThreading is built using Gradle.

* Install JDK 8. Set your JDK_HOME environment variable to your JDK 8 install path
* Checkout this repo and run: `gradlew.bat`

Coding and Pull Request Formatting
----------------------------------
* Generally follows the Oracle coding standards.
* Tabs, no spaces.
* Pull requests must compile and work.
* Pull requests must be formatted properly.
* Code should be self-documenting - when possible meaningful names and good design should make comments unnecessary

Please follow the above conventions if you want your pull requests accepted.

IRC
----------------------------------

Chat with us at [irc.esper.net #TickThreading](irc://irc.esper.net/TickThreading)

Build service alerts of new builds are available at #n-builds.

Donations
----------------------------------

Paypal: [Donate](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=PTWH8MH8Y4WH8)

Contributors
----------------------------------

* [nallar](https://github.com/nallar/ "Ross Allan")
* Everyone who's helped with testing and reporting bugs :)

Acknowledgements
----------------------------------

YourKit is kindly supporting open source projects with its full-featured Java Profiler. YourKit, LLC is the creator of innovative and intelligent tools for profiling Java and .NET applications. Take a look at YourKit's leading software products: [YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and [YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp).

[Jenkins]: https://jenkins.nallar.me/jobs/TickThreading
