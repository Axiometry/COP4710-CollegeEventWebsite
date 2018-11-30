name := "play-scala-anorm-example"

version := "2.6.0-SNAPSHOT"

scalaVersion := "2.12.6"

crossScalaVersions := Seq("2.11.12", "2.12.6")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.jcenterRepo
resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += guice
libraryDependencies += jdbc
libraryDependencies += evolutions

libraryDependencies += "com.h2database" % "h2" % "1.4.197"

libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.1"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test

libraryDependencies += "org.webjars" %% "webjars-play" % "2.6.3"
libraryDependencies += "org.webjars" % "material-design-lite" % "1.3.0"
libraryDependencies += "org.webjars.npm" % "dialog-polyfill" % "0.4.10"

libraryDependencies += "com.iheart" %% "ficus" % "1.4.3"
libraryDependencies += "net.codingwell" %% "scala-guice" % "4.1.0"

libraryDependencies += "com.mohiva" %% "play-silhouette" % "5.0.5"
libraryDependencies += "com.mohiva" %% "play-silhouette-password-bcrypt" % "5.0.5"
libraryDependencies += "com.mohiva" %% "play-silhouette-persistence" % "5.0.5"
libraryDependencies += "com.mohiva" %% "play-silhouette-crypto-jca" % "5.0.5"