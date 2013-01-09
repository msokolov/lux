/*jslint  browser: true, white: true, plusplus: true */
/*global $: true */

$(function () {
    'use strict';
    $.ajax({
        url: 'getQNames.xqy',
        dataType: 'json'
    }).done(function (data) {
        var status = $('#selection');
        $('#qname').autocomplete({
            lookup: data,
            serviceUrl:'getQNames.xqy'
        });
    });
});

$('#term').autocomplete({
    serviceUrl:'getTerms.xqy',
    /*
    fnFormatResult: function (value, data, currentValue) {
        return value.substring(value.indexOf(currentValue));
    }
    */
});
