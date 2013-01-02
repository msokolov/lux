Lux is distributed in three basic forms: as source code, as a library, and
as a complete web application (java war file).  We also provide a
preconfigured (Jetty-based) application server bundle. These are all
available for direct download as zip files from luxproject.net.  The source
code is also distributed via {source control system: GitHub?} at {scm-url},
and the binaries and source are available via maven using groupId:
net.luxdb, artifactId: luxdb.

The quickest way to get up and running with Lux is to install the complete
application bundle.  You will need to have Java 6 or greater installed in
order to run Lux:

1. Download the bundle as a zip or tar archive

2. Unpack the bundle (no installer required!).  This will create a folder
called "lux-{version#}".

3. Run Lux.  You can do this from the command line using either the windows
lux.bat file or UNIX lux file

4. Verify that lux is running by visiting http://localhost:8080/lux/demo in
your browser.  Lux comes with the Shakespeare demo installed: follow the
on-screen instructions there to load the text of all the Shakespeare plays,
try out the search, and explore the xquery source for the demo.

Once you have Lux running