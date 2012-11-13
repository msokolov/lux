xquery version "1.0";
declare namespace lux="http://luxproject.net";
declare variable $lux:http-parameter-map as element(http-parameter-map) external;
(: 
   this whole query body could have been written '$lux:http-parameter-map', but we expand
   the nodes to illustrate the contents and for testing purposes 
:)
<http-parameter-map>{
  for $param in $lux:http-parameter-map/param return
    <param>{
      $param/@name,
      for $value in $param/value
        return <value>{$value/text()}</value>
    }</param>
}</http-parameter-map>
