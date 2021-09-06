/*
 * Copyright 2021 Scala.js (https://www.scala-js.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxOptions, FirefoxProfile}
import org.openqa.selenium.remote.server.{DriverFactory, DriverProvider}

import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
import org.scalajs.jsenv.selenium.SeleniumJSEnv

import java.util.concurrent.TimeUnit

ThisBuild / baseVersion := "0.1"

ThisBuild / organization := "org.scala-js"
ThisBuild / organizationName := "Scala.js (https://www.scala-js.org/)"

ThisBuild / developers := List(
  Developer("djspiewak", "Daniel Spiewak", "@djspiewak", url("https://github.com/djspiewak")),
  Developer("armanbilge", "Arman Bilge", "@armanbilge", url("https://github.com/armanbilge")))

ThisBuild / homepage := Some(url("https://github.com/scala-js/scala-js-macrotask-executor"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/scala-js/scala-js-macrotask-executor"),
    "git@github.com:scala-js/scala-js-macrotask-executor.git"))

ThisBuild / crossScalaVersions := Seq("2.12.14", "2.13.6", "3.0.1")

ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v2.1.2"),
    name = Some("Setup NodeJS v14 LTS"),
    params = Map("node-version" -> "14"),
    cond = Some("matrix.ci == 'ciNode' || matrix.ci == 'ciJSDOMNodeJS'")),
  WorkflowStep.Run(
    List("npm install"),
    name = Some("Install jsdom"),
    cond = Some("matrix.ci == 'ciJSDOMNodeJS'")))

val ciVariants = List("ciNode", "ciFirefox", "ciChrome", "ciJSDOMNodeJS")

ThisBuild / githubWorkflowBuildMatrixAdditions += "ci" -> ciVariants

ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("${{ matrix.ci }}")))

replaceCommandAlias("ci", ciVariants.mkString("; ", "; ", ""))

addCommandAlias("ciNode", "; set useJSEnv := JSEnv.NodeJS; core/test; core/doc")
addCommandAlias("ciFirefox", "; set useJSEnv := JSEnv.Firefox; all core/test webworker/test; set useJSEnv := JSEnv.NodeJS")
addCommandAlias("ciChrome", "; set useJSEnv := JSEnv.Chrome; all core/test webworker/test; set useJSEnv := JSEnv.NodeJS")
addCommandAlias("ciJSDOMNodeJS", "; set useJSEnv := JSEnv.JSDOMNodeJS; core/test; set useJSEnv := JSEnv.NodeJS")

lazy val useJSEnv =
  settingKey[JSEnv]("Use Node.js or a headless browser for running Scala.js tests")

Global / useJSEnv := JSEnv.NodeJS

ThisBuild / Test / jsEnv := {
  import JSEnv._

  val old = (Test / jsEnv).value

  useJSEnv.value match {
    case NodeJS => old
    case JSDOMNodeJS => new JSDOMNodeJSEnv()
    case Firefox =>
      val profile = new FirefoxProfile()
      profile.setPreference("privacy.file_unique_origin", false)
      val options = new FirefoxOptions()
      options.setProfile(profile)
      options.setHeadless(true)
      new SeleniumJSEnv(options)
    case Chrome =>
      val options = new ChromeOptions()
      options.setHeadless(true)
      options.addArguments("--allow-file-access-from-files")
      val factory = new DriverFactory {
        val defaultFactory = SeleniumJSEnv.Config().driverFactory
        def newInstance(capabilities: org.openqa.selenium.Capabilities): WebDriver = {
          val driver = defaultFactory.newInstance(capabilities).asInstanceOf[ChromeDriver]
          driver.manage().timeouts().pageLoadTimeout(1, TimeUnit.HOURS)
          driver.manage().timeouts().setScriptTimeout(1, TimeUnit.HOURS)
          driver
        }
        def registerDriverProvider(provider: DriverProvider): Unit =
          defaultFactory.registerDriverProvider(provider)
      }
      new SeleniumJSEnv(options, SeleniumJSEnv.Config().withDriverFactory(factory))
  }
}

lazy val root = project
  .aggregate(core, webworker)
  .enablePlugins(NoPublishPlugin)

lazy val core = project
  .in(file("core"))
  .settings(name := "scala-js-macrotask-executor")
  .enablePlugins(ScalaJSPlugin)

// this project solely exists for testing purposes
lazy val webworker = project
  .in(file("webworker"))
  .dependsOn(core % "compile->test")
  .settings(
    name := "scala-js-macrotask-executor-webworker",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += ("org.scala-js" %%% "scalajs-dom" % "1.2.0").cross(CrossVersion.for3Use2_13),
    (Test / test) := (Test / test).dependsOn(Compile / fastOptJS).value,
    buildInfoKeys := Seq[BuildInfoKey](scalaVersion, baseDirectory),
    buildInfoPackage := "org.scalajs")
  .enablePlugins(ScalaJSPlugin, BuildInfoPlugin, NoPublishPlugin)