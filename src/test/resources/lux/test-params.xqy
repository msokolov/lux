xquery version "1.0";
declare namespace lux="http://luxproject.net";
declare variable $lux:http as document-node() external;
(: 
   this whole query body could have been written '$lux:http-parameter-map', but we expand
   the nodes to illustrate the contents and for testing purposes 
:)
<http method="{$lux:http/http/@method}"><parameters>{
  for $param in $lux:http/http/parameters/param return
    <param>{
      $param/@name,
      for $value in $param/value
        return <value>{$value/text()}</value>
    }</param>
}</parameters></http>
