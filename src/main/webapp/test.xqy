let $dblp := doc ("file:samples/dblp.xml")
let $insert := 
   for $item at $i in $dblp/dblp/*
     let $uri := concat("/dblp/", $i, ".xml")
     return (lux:insert ($uri, $item), $uri)
let $commit := lux:commit()
(: We have to "count" the results of $insert and $commit in order to 
   avoid having them be optimized away by Saxon's too-clever compiler :)
return concat ("inserted ", count(($insert, $commit)), " documents")
