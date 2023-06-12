package data.installer

import io.menu.prompts.ListPrompt
import io.menu.prompts.ListValidationRules
import data.ManifestData
import data.PreviousManifestData
import schemas.manifest.YamlExtensions.convertToList

object Protocols : ListPrompt<String> {
    override val name: String = "Protocols"

    override val validationRules: ListValidationRules<String> = ListValidationRules(
        maxItems = 64,
        maxItemLength = 2048,
        transform = ::convertToList
    )

    override val description: String = "List of protocols the package provides a handler for"

    override val extraText: String? = null

    override val default: List<String>? get() = PreviousManifestData.installerManifest?.run {
        protocols ?: installers.getOrNull(ManifestData.installers.size)?.protocols
    }
}
