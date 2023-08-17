package data

import github.GitHubDetection
import io.ktor.http.Url
import kotlinx.datetime.LocalDate
import schemas.AdditionalMetadata
import schemas.SchemaType
import schemas.installerSorter
import schemas.manifest.DefaultLocaleManifest
import schemas.manifest.InstallerManifest
import utils.ManifestUtils.updateVersionInString
import utils.Zip
import utils.filterSingleDistinctOrElse
import utils.mapDistinctSingleOrNull
import utils.msi.Msi
import utils.msix.Msix
import utils.msix.MsixBundle

object InstallerManifestData {
    suspend fun addInstaller(
        packageVersion: String,
        installerUrl: Url,
        installerSha256: String,
        installerType: InstallerManifest.InstallerType?,
        installerLocale: String? = null,
        scope: InstallerManifest.Scope? = null,
        releaseDate: LocalDate? = null,
        packageName: String? = null,
        installerSwitches: InstallerManifest.InstallerSwitches = InstallerManifest.InstallerSwitches(),
        allVersions: List<String>? = null,
        upgradeBehavior: InstallerManifest.UpgradeBehavior? = null,
        installers: List<InstallerManifest.Installer>,
        architecture: InstallerManifest.Installer.Architecture,
        additionalMetadata: AdditionalMetadata? = null,
        productCode: String? = null,
        msix: Msix?,
        msixBundle: MsixBundle?,
        msi: Msi?,
        zip: Zip?,
        gitHubDetection: GitHubDetection?,
        previousManifestData: PreviousManifestData?,
        onAddInstaller: (InstallerManifest.Installer) -> Unit
    ) {
        val previousInstallerManifest = previousManifestData?.installerManifest?.await()
        val previousInstaller = previousInstallerManifest?.installers?.getOrNull(installers.size)
        val installer = InstallerManifest.getInstallerBase(previousInstaller).copy(
            installerLocale = msi?.productLanguage
                ?: installerLocale?.ifBlank { null }
                ?: previousInstaller?.installerLocale,
            platform = msix?.targetDeviceFamily?.let(::listOf)
                ?: previousInstaller?.platform
                ?: previousInstallerManifest?.platform,
            minimumOSVersion = msix?.minVersion
                ?: (previousInstaller?.minimumOSVersion ?: previousInstallerManifest?.minimumOSVersion)
                    .takeUnless { it == "10.0.0.0" },
            architecture = previousInstaller?.architecture ?: architecture,
            installerType = installerType ?: previousInstaller?.installerType,
            nestedInstallerType = zip?.nestedInstallerType
                ?: previousInstaller?.nestedInstallerType
                ?: previousInstallerManifest?.nestedInstallerType,
            nestedInstallerFiles = (
                zip?.nestedInstallerFiles?.ifEmpty { null }
                    ?: previousInstaller?.nestedInstallerFiles
                    ?: previousInstallerManifest?.nestedInstallerFiles
                )?.map {
                    it.copy(relativeFilePath = it.relativeFilePath.updateVersionInString(allVersions, packageVersion)
                        .trimStart('/', '\\', '.'))
                },
            installerUrl = installerUrl,
            installerSha256 = (gitHubDetection?.sha256 ?: installerSha256).uppercase(),
            signatureSha256 = (msix?.signatureSha256 ?: msixBundle?.signatureSha256)?.uppercase(),
            scope = scope ?: previousInstaller?.scope ?: previousInstallerManifest?.scope,
            packageFamilyName = msix?.packageFamilyName
                ?: msixBundle?.packageFamilyName
                ?: previousInstaller?.packageFamilyName
                ?: previousInstallerManifest?.packageFamilyName,
            installerSwitches = installerSwitches.takeUnless(InstallerManifest.InstallerSwitches::areAllNullOrBlank)
                ?: previousInstaller?.installerSwitches
                ?: previousInstallerManifest?.installerSwitches,
            upgradeBehavior = upgradeBehavior
                ?: previousInstaller?.upgradeBehavior
                ?: previousInstallerManifest?.upgradeBehavior,
            productCode = productCode
                ?: additionalMetadata?.productCode?.ifBlank { null }
                ?: (previousInstallerManifest?.productCode ?: previousInstaller?.productCode)
                    ?.updateVersionInString(allVersions, packageVersion),
            releaseDate = additionalMetadata?.releaseDate ?: gitHubDetection?.releaseDate ?: releaseDate,
            appsAndFeaturesEntries = additionalMetadata?.appsAndFeaturesEntries?.let(::listOf)
                ?: previousInstaller?.appsAndFeaturesEntries?.map { appsAndFeaturesEntry ->
                    appsAndFeaturesEntry.fillARPEntry(
                        packageName, packageVersion, allVersions, msi, previousManifestData.defaultLocaleManifest, additionalMetadata
                    )
                } ?: previousInstallerManifest?.appsAndFeaturesEntries?.map { appsAndFeaturesEntry ->
                appsAndFeaturesEntry.fillARPEntry(
                    packageName, packageVersion, allVersions, msi, previousManifestData.defaultLocaleManifest, additionalMetadata
                )
            } ?: listOfNotNull(
                InstallerManifest.AppsAndFeaturesEntry()
                    .fillARPEntry(packageName, packageVersion, allVersions, msi, previousManifestData?.defaultLocaleManifest, additionalMetadata)
                    .takeUnless(InstallerManifest.AppsAndFeaturesEntry::areAllNull)
            ).ifEmpty { null },
        )
        if (msixBundle == null) {
            onAddInstaller(installer)
        } else {
            msixBundle.packages?.forEach { individualPackage ->
                individualPackage.processorArchitecture?.let { architecture ->
                    onAddInstaller(
                        installer.copy(
                            architecture = architecture,
                            platform = individualPackage.targetDeviceFamily,
                        )
                    )
                }
            }
        }
    }

    private fun InstallerManifest.AppsAndFeaturesEntry.fillARPEntry(
        packageName: String?,
        packageVersion: String,
        allVersions: List<String>?,
        msi: Msi?,
        previousDefaultLocaleData: DefaultLocaleManifest?,
        additionalMetadata: AdditionalMetadata?
    ): InstallerManifest.AppsAndFeaturesEntry {
        val arpDisplayName = additionalMetadata?.appsAndFeaturesEntries?.displayName ?: msi?.productName ?: displayName
        val name = packageName ?: previousDefaultLocaleData?.packageName
        val arpPublisher = additionalMetadata?.appsAndFeaturesEntries?.publisher ?: msi?.manufacturer ?: publisher
        val publisher = publisher ?: previousDefaultLocaleData?.publisher
        val displayVersion = additionalMetadata?.appsAndFeaturesEntries?.displayVersion
            ?: msi?.productVersion
            ?: displayVersion
        return copy(
            displayName = if (arpDisplayName != name) {
                arpDisplayName?.updateVersionInString(allVersions, packageVersion)
            } else {
                null
            },
            publisher = if (arpPublisher != publisher) arpPublisher else null,
            displayVersion = if (displayVersion != packageVersion) {
                displayVersion?.updateVersionInString(allVersions, packageVersion)
            } else {
                null
            },
            upgradeCode = additionalMetadata?.appsAndFeaturesEntries?.upgradeCode ?: msi?.upgradeCode ?: upgradeCode
        )
    }

    fun createInstallerManifest(
        packageIdentifier: String,
        packageVersion: String,
        commands: List<String>? = null,
        fileExtensions: List<String>? = null,
        protocols: List<String>? = null,
        installerSuccessCodes: List<Long>? = null,
        installModes: List<InstallerManifest.InstallModes>? = null,
        allVersions: List<String>?,
        installers: List<InstallerManifest.Installer>,
        previousInstallerManifest: InstallerManifest?,
        manifestOverride: String
    ): InstallerManifest {
        return InstallerManifest.getBase(previousInstallerManifest, packageIdentifier, packageVersion).copy(
            packageIdentifier = packageIdentifier,
            packageVersion = packageVersion,
            installerLocale = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::installerLocale)
                ?.ifBlank { null }
                ?: previousInstallerManifest?.installerLocale,
            platform = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::platform)
                ?: previousInstallerManifest?.platform,
            minimumOSVersion = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::minimumOSVersion)
                ?.ifBlank { null },
            installerType = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::installerType)
                ?: previousInstallerManifest?.installerType,
            nestedInstallerType = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::nestedInstallerType)
                ?: previousInstallerManifest?.nestedInstallerType,
            nestedInstallerFiles = (
                installers.mapDistinctSingleOrNull(InstallerManifest.Installer::nestedInstallerFiles)
                    ?: previousInstallerManifest?.nestedInstallerFiles
            )?.map {
                it.copy(relativeFilePath = it.relativeFilePath.updateVersionInString(allVersions, packageVersion))
            },
            scope = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::scope)
                ?: previousInstallerManifest?.scope,
            packageFamilyName = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::packageFamilyName)
                ?: previousInstallerManifest?.packageFamilyName,
            productCode = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::productCode),
            installModes = installModes?.ifEmpty { null }
                ?: previousInstallerManifest?.installModes,
            installerSwitches = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::installerSwitches)
                ?: previousInstallerManifest?.installerSwitches,
            installerSuccessCodes = installerSuccessCodes?.ifEmpty { null }
                ?: previousInstallerManifest?.installerSuccessCodes,
            upgradeBehavior = installers.mapDistinctSingleOrNull(InstallerManifest.Installer::upgradeBehavior)
                ?: previousInstallerManifest?.upgradeBehavior,
            commands = commands?.ifEmpty { null } ?: previousInstallerManifest?.commands,
            protocols = protocols?.ifEmpty { null } ?: previousInstallerManifest?.protocols,
            fileExtensions = fileExtensions?.ifEmpty { null } ?: previousInstallerManifest?.fileExtensions,
            releaseDate = installers.mapDistinctSingleOrNull { it.releaseDate },
            appsAndFeaturesEntries = when (installers.distinctBy { it.appsAndFeaturesEntries }.size) {
                0 -> previousInstallerManifest?.appsAndFeaturesEntries
                1 -> installers.first().appsAndFeaturesEntries
                else -> null
            },
            installers = installers.removeNonDistinctKeys(installers).sortedWith(installerSorter),
            manifestType = SchemaType.INSTALLER,
            manifestVersion = manifestOverride
        )
    }

    private fun List<InstallerManifest.Installer>.removeNonDistinctKeys(installers: List<InstallerManifest.Installer>):
        List<InstallerManifest.Installer> {
        return map { installer ->
            installer.copy(
                installerLocale = installers.filterSingleDistinctOrElse(installer.installerLocale) { it.installerLocale },
                platform = installers.filterSingleDistinctOrElse(installer.platform) { it.platform },
                minimumOSVersion = installers.filterSingleDistinctOrElse(installer.minimumOSVersion) { it.minimumOSVersion },
                installerType = installers.filterSingleDistinctOrElse(installer.installerType) { it.installerType },
                nestedInstallerType = installers
                    .filterSingleDistinctOrElse(installer.nestedInstallerType) { it.nestedInstallerType },
                nestedInstallerFiles = installers
                    .filterSingleDistinctOrElse(installer.nestedInstallerFiles) { it.nestedInstallerFiles },
                scope = installers.filterSingleDistinctOrElse(installer.scope) { it.scope },
                packageFamilyName = installers.filterSingleDistinctOrElse(installer.packageFamilyName) { it.packageFamilyName },
                productCode = installers.filterSingleDistinctOrElse(installer.productCode) { it.productCode },
                releaseDate = installers.filterSingleDistinctOrElse(installer.releaseDate) { it.releaseDate },
                upgradeBehavior = installers.filterSingleDistinctOrElse(installer.upgradeBehavior) { it.upgradeBehavior },
                installerSwitches = installers.filterSingleDistinctOrElse(installer.installerSwitches) { it.installerSwitches },
                appsAndFeaturesEntries = installers
                    .filterSingleDistinctOrElse(installer.appsAndFeaturesEntries) { it.appsAndFeaturesEntries }
            )
        }
    }
}
