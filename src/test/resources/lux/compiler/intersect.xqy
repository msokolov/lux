declare namespace local="http://localhost/";
let $x := <x> <a>a</a> <b>test</b> <c>c</c> </x>
return (subsequence($x/*,1,2) intersect subsequence($x/*,2,3))/string()
  