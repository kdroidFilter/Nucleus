/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.dsl

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class PublishSettings {
    @get:Inject
    internal abstract val objects: ObjectFactory

    val github: GitHubPublishSettings = objects.newInstance(GitHubPublishSettings::class.java)

    fun github(fn: Action<GitHubPublishSettings>) {
        fn.execute(github)
    }

    val s3: S3PublishSettings = objects.newInstance(S3PublishSettings::class.java)

    fun s3(fn: Action<S3PublishSettings>) {
        fn.execute(s3)
    }
}

@Suppress("UnnecessaryAbstractClass") // Required abstract for Gradle ObjectFactory.newInstance()
abstract class GitHubPublishSettings {
    /** Enable publishing to GitHub Releases. Default: false */
    var enabled: Boolean = false

    /** GitHub repository owner */
    var owner: String? = null

    /** GitHub repository name */
    var repo: String? = null

    /** GitHub token (or use GITHUB_TOKEN env var) */
    var token: String? = null

    /** Release channel: "latest", "beta", "alpha". Default: "latest" */
    var channel: String = "latest"

    /** Release type: "release", "draft", "prerelease". Default: "release" */
    var releaseType: String = "release"
}

@Suppress("UnnecessaryAbstractClass") // Required abstract for Gradle ObjectFactory.newInstance()
abstract class S3PublishSettings {
    /** Enable publishing to S3. Default: false */
    var enabled: Boolean = false

    /** S3 bucket name */
    var bucket: String? = null

    /** AWS region */
    var region: String? = null

    /** Path prefix within the bucket */
    var path: String? = null

    /** ACL for uploaded objects (e.g., "public-read") */
    var acl: String? = null
}
