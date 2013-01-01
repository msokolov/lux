xquery version "1.0";
declare namespace lux="http://luxproject.net";
declare variable $lux:http as document-node() external;
(: We change the name of the param tags to 'parm' because this node will be serialized as HTML,
   and in HTML, param is defined as a singleton tag: the serializer will mangle it.
 :)
<http method="{$lux:http/http/@method}"><params>{
  for $param in $lux:http/http/params/param return
    <parm>{
      $param/@name,
      for $value in $param/value
        return <value>{$value/text()}</value>
    }</parm>
}</params></http>
