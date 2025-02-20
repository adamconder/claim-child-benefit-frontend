import sbt._

object AppDependencies {

  private val bootstrapVersion = "7.13.0"
  private val hmrcMongoVersion = "0.74.0"

  val compile = Seq(
    play.sbt.PlayImport.ws,
    "uk.gov.hmrc"                   %% "play-frontend-hmrc"             % "6.4.0-play-28",
    "uk.gov.hmrc"                   %% "play-conditional-form-mapping"  % "1.12.0-play-28",
    "uk.gov.hmrc"                   %% "bootstrap-frontend-play-28"     % bootstrapVersion,
    "uk.gov.hmrc.mongo"             %% "hmrc-mongo-play-28"             % hmrcMongoVersion,
    "uk.gov.hmrc"                   %% "crypto-json-play-28"            % "7.3.0",
    "org.typelevel"                 %% "cats-core"                      % "2.3.0",
    "uk.gov.hmrc"                   %% "domain"                         % "8.1.0-play-28",
    "com.dmanchester"               %% "playfop"                        % "1.0",
    "com.googlecode.libphonenumber" % "libphonenumber"                  % "8.12.47",
    "org.apache.xmlgraphics"        % "fop"                             % "2.7"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "org.scalatest"           %% "scalatest"               % "3.2.10",
    "org.scalatestplus"       %% "scalacheck-1-15"         % "3.2.10.0",
    "org.scalatestplus"       %% "mockito-3-4"             % "3.2.10.0",
    "org.mockito"             %% "mockito-scala"           % "1.16.42",
    "org.scalacheck"          %% "scalacheck"              % "1.15.4",
    "org.pegdown"             %  "pegdown"                 % "1.6.0",
    "org.jsoup"               %  "jsoup"                   % "1.14.3",
    "com.vladsch.flexmark"    %  "flexmark-all"            % "0.62.2",
    "com.github.tomakehurst"  %  "wiremock-standalone"     % "2.27.2",
    "io.vertx"                %  "vertx-json-schema"       % "4.3.8"
  ).map(_ % "test, it")

  def apply(): Seq[ModuleID] = compile ++ test
}
