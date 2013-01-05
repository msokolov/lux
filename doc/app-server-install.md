The app server bundle includes everything (except the Java
runtime) that you need to run Lux out of the box, as well as a demo
application for searching (and reading) the complete plays of
Shakespeare. These are all available for direct download from luxdb.net
(see links below).  The source code is also distributed via {source control
system: GitHub?} at {scm-url}.

## Lux bundled with application server ##

The quickest way to get up and running with Lux is to install the complete
application bundle.  You will need to have
[Java](http://java.com/en/download/index.jsp "Download") 6 or greater
installed in order to run Lux:

1. Download the bundle as a [zip
   file](http://luxdb.net/download/lux-server-0.5.zip) "Download app server
   zip") or [tar archive](http://luxdb.net/download/lux-server-0.5.tar.gz
   "Download app server tar").

2. Unpack the bundle (no installer required!).  This will create a folder
   called "lux-0.5".

3. Run Lux.  You can do this from the command line using either the Windows
   *lux.bat* batch file or UNIX *lux* bash script.

4. Verify that Lux is running by visiting http://localhost:8080/lux/demo in
   your browser.  Lux comes with the Shakespeare demo installed: follow the
   on-screen instructions there to load the text of all the Shakespeare
   plays, try out the search, and explore the xquery source for the demo.
