# Tepid Web Server

###Setup

1. Firstly, make sure that the IntelliJ project works properly. Download [Tomcat 7.x](https://tomcat.apache.org/download-70.cgi) and make sure the exploded artifact is built and deployed when running.

1. Download and extract the tepid-sdk; see \\\\ender\software
<br>I have placed an tepid-sdk-min version in \\\\ender\Public_share without eclipse inside

1. Run setup.bat in the extracted folder. The tepid link should point to the web resources of your repo (src/main/resources)

1. Go to Apache24/conf/httpd.conf and replace all 6 instances of c:/Apache24 with the correct directory.<br>As stated in the file, replace all backslashes with forward slashes

1. Go to the java/shared package and create a Config file from the ConfigSample. Add your keys to getSettings(); this will be ignored by git

1. Run httpd.exe in tepid-sdk/Apache24/bin and run Tomcat in IntelliJ

1. Go to [http://localhost/tepid/](http://localhost/tepid/) and do your magic

###Possible Errors

If you messed up with the tepid link, you may redo it by starting a cmd prompt right in the parent of Apache24 and running
> mklink /d Apache24\htdocs\tepid %repo%\src\main\resources

Where %repo% is the root project directory

Running and testing tepid requires very specific configurations, that are uploaded to .idea for your convenience. When you import the project through IntelliJ, you can check out from VCS to save all the settings. In the off chance that you import the project and rewrite the configs, you'll need to set up tomcat yourself.
Tomcat can either be used externally if you know how or through the dropdown beside run &rarr; edit configurations. Make sure the context and paths are set to /tepid/ and that the exploded artifacts are built.

If the files are not showing inside the project, you'll need to manually import the module. Go to
> File &rarr; Project Structure &rarr; Modules &rarr; + &rarr; Import Module &rarr; point to /src/main

Keep pressing okay and it should recognize all the modules

###Info

Bower components are now added through gradle using [this plugin](https://github.com/craigburke/client-dependencies-gradle).
Since they are now pushed in the repo, you shouldn't have to reinstall them yourself. Check the plugin docs for more information.