let $delete := lux:delete()
let $commit := lux:commit()
return
<html>
  <head>
    <meta http-equiv="refresh" content="0;URL=index.xqy" />
    <script>location.href="index.xqy"</script>
  </head>
  <body>
    {$delete, $commit (: don't optimize these out of existence! :)}
    <a href="index.xqy">redirect...</a>
  </body>
</html>
