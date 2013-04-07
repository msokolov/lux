let $x := <foo><a>a</a><b>b</b></foo>

return ($x//a/following::* is $x//a/following-sibling::*)