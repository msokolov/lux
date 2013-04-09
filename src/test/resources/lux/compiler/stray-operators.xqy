declare namespace local="http://localhost/";
let $x := <x> <a>10</a> <b>2</b> <c>5</c> </x>
return - (subsequence($x/*,1) except subsequence($x/*,2)) div 5
  