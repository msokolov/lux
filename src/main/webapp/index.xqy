xquery version "1.0";

declare namespace lux="http://luxproject.net";
declare namespace demo="http://luxproject.net/demo";

declare variable $lux:http as document-node() external;

declare function demo:format-param ($p as element(param)) as xs:string
{
  fn:concat ($p/@name, "=", fn:string-join ($p/value, ","))
};

(:
  OK - the basic form evaluation / http now works. How can we showcase 
  integration w/Lucene?
  1. load documents
  2. show all element/attribute names (keys from the QName index)
  3. autocomplete all words in that scope (from the ElementTextField, removing the QName prefix)
  4. search for matching documents, showing title and snippet(s)
  pagination (no sorting?)

  Error reporting is poor - we get the last of any syntax errors that 
  are reported by Saxon - the first (or all) would be better
:)
<html>
  <head>
    <title>Lux Demo</title>
    <link href="styles.css" rel="stylesheet" />
  </head>
  <body>
    <form action="index.xqy" id="search">
      <div class="container">
        <h1>Lux Demo</h1>
        <div>
          <input type="text" name="query" id="query"/>
        </div>
        <div id="selection"></div>
      </div>
      <input name="q" /><input type="submit" value="search" />
    </form>
    <p id="search-description">{
      for $param in $lux:http/http/parameters/param 
      return demo:format-param ($param)
    }</p>
    <script src="js/jquery-1.8.2.min.js"></script>
    <script src="js/jquery.autocomplete.js"></script>
    <script src="js/getQNames.js"></script>
  </body>
</html>