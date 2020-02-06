package ca.mcgill.science.tepid.server.util

import ca.mcgill.science.tepid.utils.DefaultProps
import ca.mcgill.science.tepid.utils.FilePropLoader
import ca.mcgill.science.tepid.utils.JarPropLoader

fun setDefaultProps(){
    DefaultProps.withName = { fileName ->
        listOf(
            FilePropLoader("/etc/tepid/$fileName"),
            FilePropLoader("webapps/tepid/$fileName"),
            JarPropLoader("/$fileName"),
            FilePropLoader("/config/$fileName")
        )
    }
}