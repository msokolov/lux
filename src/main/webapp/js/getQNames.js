/*jslint  browser: true, white: true, plusplus: true */
/*global $: true */

$(function () {
    'use strict';
    $.ajax({
        url: 'getQNames.xqy',
        dataType: 'json'
    }).done(function (data) {
        var status = $('#selection');
        $('#query').autocomplete({
            lookup: data,
            serviceUrl:'getQNames.xqy'
        });
    });
});