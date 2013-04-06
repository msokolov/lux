let $doc as element(top) := <top>
  <parent id="1">
    <child />
  </parent>
  <parent id="2">
    <child />
  </parent>
</top>

return count ($doc//child/..)