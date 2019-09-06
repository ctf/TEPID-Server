# Tepid Server

Welcome to the source code of Tepid Server.

Relevant Repositories:

* [Tepid Commons](https://gitlab.science.mcgill.ca/TEPID/tepid-commons)
* [Tepid Web](https://gitlab.science.mcgill.ca/TEPID/tepid-web)
* [Tepid Client](https://gitlab.science.mcgill.ca/TEPID/client)

---

## Intro

TEPID is a print server. Your users send their print jobs to TEPID, and TEPID sends them to a printer. This is how print servers do.
TEPID has several advantages over other print servers:
* __Automatically mounts printers:__ This means that your end-users don't have to mount the printers manually. The default printer for hosts can also be configured serverside with a regex match. This allows you to automatically assign sane defaults without your users having to thing about it. It also could allow you to reroute prints in case of a downed printer, although this seems risky.
* __Load-balanced Queues:__ It can form a queue and load balance between printers. For example, one could have 2 printers in a printing room. Users can see a single printer labeled "Print Room" and can simply send print jobs to it. The server will load balance between the printers automatically. A notification will inform the user which printer a print job went to.
* __Printers can be marked "Down" independently:__ Printer status can be configured through the web interface or through the API. This allows you to mark printers as "Down" when they are in need of maintenance. TEPID will not send jobs to that printer. Printers in a Queue can also be independently marked down; a queue will only be marked down (from the users point of view) if all printers in a queue are marked down. 
* __Resource Usage Tracking:__ Track how many pages your users are printing. Also tracks color pages, and counts them for 3 pages. The resource-tracking algorithm also only counts individual colour pages; for example, a 10-page greyscale document with a single colour cover page will count for 1 colour page and 10 greyscale pages, which is generally how one would be charged for such things.
* __Clean Web Interface:__ An easy-to-use web interface allows easy management of all the things Tier 1 techs or people operating a print pool would need to do, including changing the status of printers and refunding failed print jobs. Additional rights can be delegated to configure the queues themselves.
* __Easily Deployed Client:__ The TEPID client builds to a JAR, which can be deployed easily enough, but it also uses WIX to build an MSI for easy deployment on Windows systems with Active Directory.
* __Extra Informational Screensaver:__ The TEPID screensaver displays the time and room of people's print jobs, so if they forgot where it was sent they can look and find out. It also displays the statuses of the print queues themselves. Check it out! 

Tepid Server acts as Tepid's REST API to fulfill various requests for the printing service.
In most cases, this simply means transferring data to and from our [database](#couchdb), but there are other instances (such as login and printing files) where some more extensive processes occur.

## Getting Started

Building Tepid requires Java 8 & a way of compiling with Gradle.

Firstly, have access to the following:

* Some IDE - [IntelliJ](https://www.jetbrains.com/idea/download/)
* Git - [Git Bash](https://git-scm.com/downloads)

Procedure:

1. Download the repo
1. Open the project in your IDE (`import project > select folder`)
1. Add your properties. See the tepid-commons repo for more info.
1. Build the war file: `gradle war`. For IntelliJ, open `Gradle` on the right side, click the green gradle button, type `war`, and press enter. This builds the file holding all the Tepid data, which is used for deployment.

That's it!

There's also a convenient GitlabCI pipeline set up to handle testing, staging, and building the WAR. Much of testing involves simply running the unit tests or making use of integration. Whenever a commit is pushed to a non test branch (starting with `test-` or `wip-`), the files will be rebuild and deployed at `http://testpid.science.mcgill.ca/`. There is a public endpoint located at `http://testpid.science.mcgill.ca:8443/tepid/about` to see the status of the deployment.

## Configuration

Configuration is spelled out fully in the tepid-commons repository's readme. 

Configuration files can be bundled with the WAR at compile time, in the same way that they are for the other TEPID project. Configurations are defined in the common config files. The configs use the TEPID standard configuration system. This allows them to be defined once, outside of the project's file tree, and used across all TEPID programs. The Gradle task copyConfigs will copy the configs from the project property "tepid_config_dir", which can be set in a number of ways:
- As a standard project property
- As a project property passed by command line ("-Ptepid_config_dir=CONFIG_DIR")
- As an environment variable "tepid_config_dir"
A lookup for the environment variable only occurs if the project property is not defined in one of the other two ways.

Configuration files can also be modified on the server, similarly to other applications one could install. Config files can be placed in /etc/tepid/ . These files will supersede those bundled in the WAR. This allows you to better care for their configuration using a configuration management tool, without having to rebuild the WAR.

The TEPID server depends on DB, LDAP, LDAPGroups, LDAPResource, LDAPTest, TEM, and URL property files. 

## Endpoints

All of our REST endpoints are located under `src/main/java/ca/mcgill/science/tepid/server/rest`, and you will notice that there are quite a few annotations per method. They are used to help specify the types of data sent/received, the permission levels required, etc.

In general, an endpoint method will have the following structure:

`@Path` data. This indicates where the endpoint is located.
To access the endpoint, a request needs to be made at `[base url]/[class @Path]/[method @Path]`

Paths with variables may be achieved by adding a `/{data}/` segment, where `data` is any string surrounded by braces.
The method must then take in some variable labelled with `@PathParam("data")`, where the path param matches the name in the path.

---

| | One of the following HTTP methods |
|---|---|
| | Must be provided, as this dictates how the endpoint is handled
`@GET` | Retrieve information without modifying data 
`@PUT` | Create or update an entity
`@POST` | Request an action to be applied to the provided entity

---

| | Optional annotations for `@Produces` / `@Consumes` |
|---|---|
| | Helps provide more information on the type of data to expect. Produces should always be annotated
`MediaType.APPLICATION_JSON` | Must adhere to JSON format
`MediaType.TEXT_PLAIN` | Any content type that can be in a String format

---

| | Optional role restrictions (`@RolesAllowed`) |
|---|---|
| | Adds restrictions based on the user request. If the criteria is not met, the request is automatically rejected
"user" | Anyone defined as a user
"ctfer" | Anyone defined as a member of CTF
"elder" | Anyone on council

---

| | Optional method header parameters |
|---|---|
| | Additional data provided by tomcat during the request
`@Context ctx: ContainerRequestContext` | Used for all methods dealing with sessions. This allows us to easily get the session and reject it using <br> `val session = ctx.getSession()
| `@Suspended ar: AsyncResponse` | Used to handle long running responses. Instead of returning data once, an `ar` may consume data until it is done or cancelled
| `@Context uriInfo: UriInfo` | For uri info. (<b>TODO remove as we don't seem to use it</b>)

## DB

The DB has a neat layer for abstracting interaction. The DB uses Hibernate to interface with a variety of DB types, but we only use Postgres. 

## Misc Functionality

## Logging

Tepid's logging is based off of Apache's log4j2. To make things simple, there are a few helper functions to bind logging functionality.

Logging is straightforward. Assuming you have a logger `log`,

* Log errors with `log.error(msg)`.
* Log errors with `log.error(msg, exception)`
* Log misc content with `log.debug(msg)` or `log.info(msg)` (debug is more verbose than info)
* Log very verbose content with `log.trace(msg)`. This is automatically disabled when debug mode is off.

You may create a logger using:

### Directly through Apache
See `LogManager.getLogger(...)`

### Through Companions
Loggers are typically per class type. You can create it directly in the class,
but the best practice is to only make one per all instance of the same class.

To do so, create a companion object extending our logger:

```kotlin
companion object : WithLogging()
```

This will lazy load a logger when it is first used, and the class can call the methods through a `log` variable.

If your companion already extends something, use the delegation pattern:

```kotlin
companion object : Loggable by WithLogging()
```

## VMs

Testing is done through a docker-compose deployment. The production system is still on a VM.

Our main VM is at `tepid.science.mcgill.ca`, and our test VM is at `testpid.science.mcgill.ca`.

Useful directories:
* `/var/lib/tomcat8/webapps/` - location of .war file
* `/var/log/tomcat8/` - location of all log related files, notably `catalina.out`

Useful commands:
* `sudo systemctl [restart|start|stop|status] tomcat8` - for updating tomcat