buildscript {
    // AGP 9.4.1 currently pulls several older build-tool-only transitives.
    // Keep its plugin classpath on patched releases until upstream catches up.
    configurations.classpath {
        resolutionStrategy.force(
            "org.jdom:jdom2:2.0.6.1",
            "org.apache.httpcomponents:httpclient:4.5.14",
            "org.apache.commons:commons-lang3:3.20.0",
            "org.bitbucket.b_c:jose4j:0.9.6",
            "org.bouncycastle:bcprov-jdk18on:1.86",
            "org.bouncycastle:bcpkix-jdk18on:1.86",
            "org.bouncycastle:bcutil-jdk18on:1.86"
        )
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
