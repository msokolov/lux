xquery version "1.0";

declare namespace lux="http://luxproject.net";
declare namespace file="http://expath.org/ns/file";
declare namespace demo="http://luxproject.net/demo";

import module namespace layout="http://luxproject.net/demo/layout" at "layout.xqy";

declare variable $lux:http as document-node() external;

let $path := ($lux:http/http/path-extra, '.')[. ne ''][1]
let $dir := if (substring($path,1,1) eq '/') then substring($path, 2) else $path
let $dirname := tokenize($dir, '/')[last()]
let $uri := $lux:http/http/@uri
let $parent := replace ($uri, "/[^/]+$", "")
let $files := file:list ($dir)
let $body := 
<div id="browse-list">
  <p><a href="{$parent}">{$parent}</a>/{$dirname}</p>
  <form action="/lux/load.xqy" method="post">
    <p><input type="submit" value="load" /></p>
    {
    for $file in $files
      let $abs-file := concat($dir,'/',$file)
      let $is-dir := file:is-dir ($abs-file)
      where not (matches($file, "^\..*$"))
      return
      <div>
        <input type="checkbox" name="selection" value="{$abs-file}" />
        <image src="/lux/img/{if ($is-dir) then 'folder' else 'document'}.png" />
        <span class="file">{
          if ($is-dir) then 
          <a href="{$uri}/{$file}">{$file}</a>
        else
          $file
        }</span>
    </div>
  }</form>
</div>
return layout:outer ($lux:http/http/@uri, $body)
