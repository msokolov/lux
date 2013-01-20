declare namespace local="http://localhost/";

concat(
  if (every $x in (1, 2, 3) satisfies $x lt 4) then "yes" else "no",
  if (some $x in (1, 2, 3) satisfies $x lt 0) then "yes" else "no"
)