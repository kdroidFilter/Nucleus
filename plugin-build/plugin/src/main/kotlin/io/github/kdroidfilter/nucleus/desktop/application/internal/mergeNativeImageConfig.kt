@file:Suppress("ktlint:standard:filename")

package io.github.kdroidfilter.nucleus.desktop.application.internal

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File

/**
 * Merges the agent-generated `reachability-metadata.json` into the existing one.
 *
 * Strategy: for each type entry, the **existing** entry is kept as the base and only
 * new information from the agent is added (new types, new methods/fields on existing types).
 * Manual enrichments like `allDeclaredFields: true` are never overwritten by the agent's
 * narrower view.
 */
internal fun mergeReachabilityMetadata(
    agentDir: File,
    targetDir: File,
) {
    val agentFile = File(agentDir, "reachability-metadata.json")
    val targetFile = File(targetDir, "reachability-metadata.json")

    if (!agentFile.exists()) return

    val slurper = JsonSlurper()

    @Suppress("UNCHECKED_CAST")
    val agentRoot = slurper.parseText(agentFile.readText()) as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    val targetRoot =
        if (targetFile.exists()) {
            slurper.parseText(targetFile.readText()) as MutableMap<String, Any?>
        } else {
            mutableMapOf()
        }

    // Merge type-based sections (reflection, jni)
    for (sectionName in listOf("reflection", "jni")) {
        @Suppress("UNCHECKED_CAST")
        val agentArray = agentRoot[sectionName] as? List<Map<String, Any?>> ?: continue

        @Suppress("UNCHECKED_CAST")
        val targetArray =
            (targetRoot[sectionName] as? MutableList<MutableMap<String, Any?>>)
                ?: mutableListOf<MutableMap<String, Any?>>().also { targetRoot[sectionName] = it }

        mergeTypeEntries(agentArray, targetArray)
    }

    // For resources/bundles/serialization, just add new entries by JSON equality
    for (sectionName in listOf("resources", "bundles", "serialization")) {
        @Suppress("UNCHECKED_CAST")
        val agentArray = agentRoot[sectionName] as? List<Map<String, Any?>> ?: continue

        @Suppress("UNCHECKED_CAST")
        val targetArray =
            (targetRoot[sectionName] as? MutableList<Map<String, Any?>>)
                ?: mutableListOf<Map<String, Any?>>().also { targetRoot[sectionName] = it }

        mergeSimpleEntries(agentArray, targetArray)
    }

    targetDir.mkdirs()
    targetFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(targetRoot)) + "\n")
}

/**
 * Merges type-based entries (reflection, jni sections).
 * For each agent entry:
 * - If the type doesn't exist in target -> add it
 * - If it exists -> merge methods/fields (add new ones, keep existing)
 * - Never remove or downgrade existing flags (allDeclaredFields, etc.)
 */
private fun mergeTypeEntries(
    agentArray: List<Map<String, Any?>>,
    targetArray: MutableList<MutableMap<String, Any?>>,
) {
    val targetIndex = linkedMapOf<String, MutableMap<String, Any?>>()
    for (entry in targetArray) {
        val typeName = entry["type"] as? String ?: continue
        @Suppress("UNCHECKED_CAST")
        targetIndex[typeName] = entry
    }

    for (agentEntry in agentArray) {
        val typeName = agentEntry["type"] as? String ?: continue
        val existingEntry = targetIndex[typeName]

        if (existingEntry == null) {
            // New type -- add as-is
            val mutableCopy = agentEntry.toMutableMap()
            targetArray.add(mutableCopy)
            targetIndex[typeName] = mutableCopy
        } else {
            // Merge into existing, preserving manual enrichments
            mergeTypeEntry(agentEntry, existingEntry)
        }
    }
}

/**
 * Merges an agent-generated type entry into an existing one.
 * Only adds new methods/fields; never removes or downgrades existing config.
 */
private fun mergeTypeEntry(
    agentEntry: Map<String, Any?>,
    existingEntry: MutableMap<String, Any?>,
) {
    // Preserve broad flags -- only upgrade false->true, never downgrade
    val broadFlags =
        listOf(
            "allDeclaredFields",
            "allDeclaredMethods",
            "allDeclaredConstructors",
            "allPublicFields",
            "allPublicMethods",
            "allPublicConstructors",
            "unsafeAllocated",
            "jniAccessible",
        )
    for (flag in broadFlags) {
        if (agentEntry[flag] == true) {
            existingEntry[flag] = true
        }
        // If existing already has it true, keep it
    }

    // Merge array-based members (methods, fields, queriedMethods)
    for (memberKey in listOf("methods", "fields", "queriedMethods")) {
        @Suppress("UNCHECKED_CAST")
        val agentMembers = agentEntry[memberKey] as? List<Map<String, Any?>> ?: continue

        // If existing has allDeclared* for this category, skip -- already broader
        val allDeclaredKey =
            when (memberKey) {
                "fields" -> "allDeclaredFields"
                "methods", "queriedMethods" -> "allDeclaredMethods"
                else -> null
            }
        if (allDeclaredKey != null && existingEntry[allDeclaredKey] == true) {
            continue
        }

        @Suppress("UNCHECKED_CAST")
        val existingMembers =
            (existingEntry[memberKey] as? MutableList<Map<String, Any?>>)
                ?: mutableListOf<Map<String, Any?>>().also { existingEntry[memberKey] = it }

        mergeMembers(agentMembers, existingMembers)
    }
}

/**
 * Adds new method/field entries from agent that don't already exist in target.
 * Identity is based on "name" + "parameterTypes" (for methods).
 */
private fun mergeMembers(
    agentMembers: List<Map<String, Any?>>,
    existingMembers: MutableList<Map<String, Any?>>,
) {
    val existingSignatures = existingMembers.map { memberSignature(it) }.toMutableSet()

    for (agentMember in agentMembers) {
        val sig = memberSignature(agentMember)
        if (sig !in existingSignatures) {
            existingMembers.add(agentMember)
            existingSignatures.add(sig)
        }
    }
}

/**
 * Produces a comparable signature string for a method/field entry.
 */
private fun memberSignature(obj: Map<String, Any?>): String {
    val name = obj["name"] as? String ?: ""

    @Suppress("UNCHECKED_CAST")
    val params = (obj["parameterTypes"] as? List<String>)?.joinToString(",") ?: ""
    return "$name($params)"
}

/**
 * For simple entries (resources, bundles), adds entries from agent that don't
 * already exist in target. Comparison is by JSON string equality.
 */
private fun mergeSimpleEntries(
    agentArray: List<Map<String, Any?>>,
    targetArray: MutableList<Map<String, Any?>>,
) {
    val existingStrings = targetArray.map { JsonOutput.toJson(it) }.toMutableSet()
    for (entry in agentArray) {
        val str = JsonOutput.toJson(entry)
        if (str !in existingStrings) {
            targetArray.add(entry)
            existingStrings.add(str)
        }
    }
}

/**
 * Merges individual JSON array config files (reflect-config.json, jni-config.json, etc.)
 * that the agent may generate in the old format.
 */
internal fun mergeJsonArrayConfig(
    agentFile: File,
    targetFile: File,
) {
    if (!agentFile.exists()) return

    val slurper = JsonSlurper()

    @Suppress("UNCHECKED_CAST")
    val agentArray = slurper.parseText(agentFile.readText()) as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    val targetArray =
        if (targetFile.exists()) {
            (slurper.parseText(targetFile.readText()) as List<Map<String, Any?>>).toMutableList()
        } else {
            mutableListOf()
        }

    @Suppress("UNCHECKED_CAST")
    val mutableTarget = targetArray as MutableList<MutableMap<String, Any?>>
    mergeTypeEntries(agentArray, mutableTarget)

    targetFile.parentFile.mkdirs()
    targetFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(targetArray)) + "\n")
}
