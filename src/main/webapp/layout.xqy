module namespace layout = "http://www.luxproject.net/layout";

declare function layout:outer ($body as node()*) 
{
<html>
  <head>
    <title>Lux Demo</title>
    <link href="/lux/styles.css" rel="stylesheet" />
  </head>
  <body>
    <h1><img class="logo" src="/lux/img/sunflwor52.png" alt="Lux" height="40" /> Lux Demo</h1>{
      $body
  }</body>
</html>
};
