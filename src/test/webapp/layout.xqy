module namespace layout = "http://luxproject.net/demo/layout";

import module namespace config="http://luxproject.net/demo/config" at "config.xqy";

declare function layout:render-nav ($current-url as xs:string)
{
let $nav := (
  <item url="{$config:root-url}browse.xqy">source</item>,
(:  <item url="{$config:root-url}index.xqy">search</item>, :)
  <item url="{$config:root-url}maven-get.xqy">maven</item>
)
return <div><ul class="hlist">{
  for $item in $nav 
  return if ($item/@url eq $current-url) 
    then <li class="selected">{$item/string()}</li>
  else <li><a href="{$item/@url}">{$item/string()}</a></li>
}</ul></div>
};

declare function layout:outer ($current-url as xs:string, $body as node()*) 
{
<html>
  <head>
    <title>Lux Demo</title>
    <link href="{$config:root-url}styles.css" rel="stylesheet" />
  </head>
  <body>
    <h1>
      <a href="{$config:root-url}index.xqy">
        <img class="logo" src="{$config:root-url}img/sunflwor52.png" alt="Lux" height="40" /> Lux Demo
      </a>
    </h1>
    {
      layout:render-nav ($current-url)
    }
    <div id="main">{
      $body
    }</div>
  </body>
</html>
};
