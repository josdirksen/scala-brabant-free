name := "scala-brabant-free"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.scalaz"           %% "scalaz-core"            % "7.2.1"
libraryDependencies += "org.scalaz"           %% "scalaz-effect"          % "7.2.1"
libraryDependencies += "org.scalaz"           %% "scalaz-concurrent"      % "7.2.1"
libraryDependencies += "ch.qos.logback"        % "logback-classic"        % "1.1.3"
libraryDependencies += "org.specs2"           %% "specs2"                 % "3.7" % "test"
libraryDependencies += "net.caoticode.buhtig" %% "buhtig"                 % "0.3.1"