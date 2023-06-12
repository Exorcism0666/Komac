package data.installer

import io.menu.prompts.ListPrompt
import io.menu.prompts.ListValidationRules
import data.ManifestData
import data.PreviousManifestData
import schemas.manifest.YamlExtensions.convertToList

object FileExtensions : ListPrompt<String> {
    override val name: String = "File extensions"

    override val description: String = "List of file extensions the package could support"

    override val extraText: String? = null

    override val validationRules: ListValidationRules<String> = ListValidationRules(
        maxItems = 512,
        maxItemLength = 64,
        minItemLength = 1,
        transform = ::convertToList,
        regex = Regex("^[^\\\\/:*?\"<>|\\x01-\\x1f]+$")
    )

    override val default: List<String>? get() = PreviousManifestData.installerManifest?.run {
        fileExtensions ?: installers.getOrNull(ManifestData.installers.size)?.fileExtensions
    }
}
