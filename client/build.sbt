name := "com.secureclient"
 
version := "1.0"
 
scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.3.13"
  val sprayV = "1.3.3"
  Seq(
    "io.spray"            %%  "spray-json"    % "1.3.2",
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %% "spray-http"     % sprayV,
    "io.spray"            %% "spray-client"   % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %% "akka-remote"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test", 
    "commons-codec" % "commons-codec" % "1.9"

  )
}
