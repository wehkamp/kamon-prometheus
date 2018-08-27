/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

resolvers ++= Seq(
    Resolver.bintrayRepo("kamon-io", "snapshots"),
    Resolver.url("blaze-plugin-releases", url("https://dl.bintray.com/blaze-plugins/releases"))(Resolver.ivyStylePatterns))

val kamonCore = "io.kamon"      %% "kamon-core" % "1.1.5" % "compile"
val nanohttpd = "org.nanohttpd" %  "nanohttpd"  % "2.3.1" % "compile"

val logbackClassic = "ch.qos.logback"   % "logback-classic" % "1.2.3" % "test"
val scalatest      = "org.scalatest"    %% "scalatest"      % "3.0.5" % "test"

lazy val root = (project in file("."))
  .enablePlugins(blaze.sbt.BlazeLibPlugin)
  .settings(name := "kamon-prometheus")
  .settings(
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    libraryDependencies ++=
      Seq(
        kamonCore,
        nanohttpd,
        scalatest,
        logbackClassic))
