package io.github.kdroidfilter.nucleus

// We write explicitly about OptIn, because IDEA doesn't suggest it.
@RequiresOptIn(
    "This library is experimental and can be unstable. Add @OptIn(io.github.kdroidfilter.nucleus.ExperimentalNucleusLibrary::class) annotation.",
)
@Deprecated("Please specify dependency via version catalog")
annotation class ExperimentalNucleusLibrary
