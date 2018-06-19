# [<img src="ao-logo.png" alt="AO Logo" width="35" height="40">](https://github.com/aoindustries) [AO TempFiles](https://github.com/aoindustries/ao-tempfiles)
<p>
	<a href="https://aoindustries.com/life-cycle#project-alpha">
		<img src="https://aoindustries.com/ao-badges/project-alpha.svg" alt="project: alpha" />
	</a>
	<a href="https://aoindustries.com/life-cycle#management-preview">
		<img src="https://aoindustries.com/ao-badges/management-preview.svg" alt="management: preview" />
	</a>
	<a href="https://aoindustries.com/life-cycle#packaging-developmental">
		<img src="https://aoindustries.com/ao-badges/packaging-developmental.svg" alt="packaging: developmental" />
	</a>
	<br />
	<a href="https://docs.oracle.com/javase/6/docs/api/">
		<img src="https://aoindustries.com/ao-badges/java-6.svg" alt="java: &gt;= 6" />
	</a>
	<a href="http://semver.org/spec/v2.0.0.html">
		<img src="https://aoindustries.com/ao-badges/semver-2.0.0.svg" alt="semantic versioning: 2.0.0" />
	</a>
	<a href="https://www.gnu.org/licenses/lgpl-3.0">
		<img src="https://aoindustries.com/ao-badges/license-lgpl-3.0.svg" alt="license: LGPL v3" />
	</a>
</p>

Java temporary file API filling-in JDK gaps and deficiencies.

## Project Links
* [Project Home](https://aoindustries.com/ao-tempfiles/)
* [Changelog](https://aoindustries.com/ao-tempfiles/changelog)
* [API Docs](https://aoindustries.com/ao-tempfiles/apidocs/)
* [Maven Central Repository](https://search.maven.org/#search%7Cgav%7C1%7Cg:%22com.aoindustries%22%20AND%20a:%22ao-tempfiles%22)
* [GitHub](https://github.com/aoindustries/ao-tempfiles)

## Modules
* [AO TempFiles Servlet](https://github.com/aoindustries/ao-tempfiles-servlet)

## Features
* Small and simple API for dealing with temporary files.
* Small footprint, self-contained, no transitive dependencies - not part of a big monolithic package.
* Java 1.6 implementation:
    * Android compatible.
    * Java EE 6+ compatible.

## Motivation
The Java language has a long-term and [well-documented](https://stackoverflow.com/questions/40119188/memory-leak-on-deleteonexithook) memory leak when using [File.deleteOnExit()](https://docs.oracle.com/javase/6/docs/api/java/io/File.html#deleteOnExit()).  The [bug report](https://bugs.openjdk.java.net/browse/JDK-4872014) has been open for 14 years as of the time of this writing.

We desire to not have to choose between a memory leak and garbage files possibly left behind on shutdown, thus this API was born.

## Alternatives
There may be functionality that is also found in other projects.  Please let us know if you find a small, clean, focused alternative.  We always love to cure ignorance - especially our own.

## Contact Us
For questions or support, please [contact us](https://aoindustries.com/contact):

Email: [support@aoindustries.com](mailto:support@aoindustries.com)  
Phone: [1-800-519-9541](tel:1-800-519-9541)  
Phone: [+1-251-607-9556](tel:+1-251-607-9556)  
Web: https://aoindustries.com/contact
