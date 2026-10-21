package fr.citoyende2027.core.data.pack

/** Pack installation, or null when no public key is configured (nothing unsigned is installed). */
class PackRuntime(val installer: PackInstaller?)
