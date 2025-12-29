# [<img src="ao-logo.png" alt="AO Logo" width="35" height="40">](https://github.com/ao-apps) [AO OSS](https://github.com/ao-apps/ao-oss) / [TempFiles](https://github.com/ao-apps/ao-tempfiles)

[![project: current stable](https://oss.aoapps.com/ao-badges/project-current-stable.svg)](https://aoindustries.com/life-cycle#project-current-stable)
[![management: production](https://oss.aoapps.com/ao-badges/management-production.svg)](https://aoindustries.com/life-cycle#management-production)
[![packaging: active](https://oss.aoapps.com/ao-badges/packaging-active.svg)](https://aoindustries.com/life-cycle#packaging-active)  
[![java: &gt;= 11](https://oss.aoapps.com/ao-badges/java-11.svg)](https://docs.oracle.com/en/java/javase/11/docs/api/)
[![semantic versioning: 2.0.0](https://oss.aoapps.com/ao-badges/semver-2.0.0.svg)](https://semver.org/spec/v2.0.0.html)
[![license: LGPL v3](https://oss.aoapps.com/ao-badges/license-lgpl-3.0.svg)](https://www.gnu.org/licenses/lgpl-3.0)

[![Build](https://github.com/ao-apps/ao-tempfiles/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/ao-apps/ao-tempfiles/actions?query=workflow%3ABuild)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.aoapps/ao-tempfiles/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.aoapps/ao-tempfiles)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?branch=master&project=com.aoapps%3Aao-tempfiles&metric=alert_status)](https://sonarcloud.io/dashboard?branch=master&id=com.aoapps%3Aao-tempfiles)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?branch=master&project=com.aoapps%3Aao-tempfiles&metric=ncloc)](https://sonarcloud.io/component_measures?branch=master&id=com.aoapps%3Aao-tempfiles&metric=ncloc)  
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?branch=master&project=com.aoapps%3Aao-tempfiles&metric=reliability_rating)](https://sonarcloud.io/component_measures?branch=master&id=com.aoapps%3Aao-tempfiles&metric=Reliability)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?branch=master&project=com.aoapps%3Aao-tempfiles&metric=security_rating)](https://sonarcloud.io/component_measures?branch=master&id=com.aoapps%3Aao-tempfiles&metric=Security)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?branch=master&project=com.aoapps%3Aao-tempfiles&metric=sqale_rating)](https://sonarcloud.io/component_measures?branch=master&id=com.aoapps%3Aao-tempfiles&metric=Maintainability)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?branch=master&project=com.aoapps%3Aao-tempfiles&metric=coverage)](https://sonarcloud.io/component_measures?branch=master&id=com.aoapps%3Aao-tempfiles&metric=Coverage)

Java temporary file API filling-in JDK gaps and deficiencies.

## Project Links
* [Project Home](https://oss.aoapps.com/tempfiles/)
* [Changelog](https://oss.aoapps.com/tempfiles/changelog)
* [API Docs](https://oss.aoapps.com/tempfiles/apidocs/)
* [Central Repository](https://central.sonatype.com/artifact/com.aoapps/ao-tempfiles)
* [GitHub](https://github.com/ao-apps/ao-tempfiles)

## Modules
* [AO TempFiles Servlet](https://github.com/ao-apps/ao-tempfiles-servlet)

## Features
* Small and simple API for dealing with temporary files.
* Small footprint, self-contained, no transitive dependencies - not part of a big monolithic package.

## Motivation
The Java language has a long-term and [well-documented](https://stackoverflow.com/questions/40119188/memory-leak-on-deleteonexithook) memory leak when using [File.deleteOnExit()](https://docs.oracle.com/javase/7/docs/api/java/io/File.html#deleteOnExit()).  The [bug report](https://bugs.openjdk.org/browse/JDK-4872014) has been open for 14 years as of the time of this writing.

We desire to not have to choose between a memory leak and garbage files possibly left behind on shutdown, thus this API was born.

## Alternatives
There may be functionality that is also found in other projects.  Please let us know if you find a small, clean, focused alternative.  We always love to cure ignorance - especially our own.

## Contact Us
For questions or support, please [contact us](https://aoindustries.com/contact):

Email: [support@aoindustries.com](mailto:support@aoindustries.com)  
Phone: [1-800-519-9541](tel:1-800-519-9541)  
Phone: [+1-251-607-9556](tel:+1-251-607-9556)  
Web: https://aoindustries.com/contact
