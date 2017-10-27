function mailchimpApiSubmit(e, form, success, fail){
  e.preventDefault();
  $.ajax({
    type: "POST",
    url: form.action,
    contentType: "text/plain",
    data: $("input." + form.id + "-input").val(),
    success: function(a, b){
      if (typeof success === "function") {
        success();
      }
    },
    error: function(a, b){
      if (typeof fail === "function") {
        fail();
      }
    }
  });
}

function getCookie(name) {
    var dc = document.cookie;
    var prefix = name + "=";
    var begin = dc.indexOf("; " + prefix);
    if (begin == -1) {
        begin = dc.indexOf(prefix);
        if (begin != 0) return null;
    }
    else
    {
        begin += 2;
        var end = document.cookie.indexOf(";", begin);
        if (end == -1) {
        end = dc.length;
        }
    }
    // because unescape has been deprecated, replaced with decodeURI
    //return unescape(dc.substring(begin + prefix.length, end));
    return decodeURI(dc.substring(begin + prefix.length, end));
}

function cookieName(name){
  var h = window.location.hostname.split(".")[0];
  var prefix = "";
  switch(h) {
    case "localhost":
      prefix = "localhost-";
      break;
    case "staging":
      prefix = "staging-";
      break;
  }
  return prefix + name;
}

$(document).ready(function(){
  // var tif = getParameterByName("tif");
  // var confirm = getParameterByName("confirm");
  // var rewriteUrl = window.location.pathname + window.location.hash;
  // window.history.pushState({}, document.title, rewriteUrl);
  // if (tif) {
  //   $(".try-it-form-central-input").focus();
  // }

  // $("#try-it-form-central").submit( function(e){
  //   mailchimpApiSubmit(e, this, function(){
  //     $(".carrot-box-thanks-top").show();
  //     $(".try-it-combo-field-top").hide();
  //   }, function(){});
  //   return false;
  // });

  // $("#try-it-form-bottom").submit( function(e){
  //   mailchimpApiSubmit(e, this, function(){
  //     $(".carrot-box-thanks-bottom").show();
  //     $(".try-it-combo-field-bottom").hide();
  //   }, function(){});
  //   return false;
  // });

  // if (confirm) {
  //   $(".confirm-thanks").show();
  //   $(".carrot-box-thanks-top").hide();
  //   $("#try-it-form-central").hide();
  // }

  var jwt = getCookie(cookieName("jwt"));
  if (jwt) {
    $("#site-header-login-item").hide();
    // Remove the get started centered button if the user is signed out
    $("#get-started-centred-bt").css({"display": "none"});
    // Remove the label below it too
    $("#easy-setup-label").css({"display": "none"});
    // Top right corner became Your Boards
    $("#site-header-signup-item").text( "Your Boards" );
    var url = "/login",
        decoded_jwt;
    if ( typeof jwt_decode === "function" ) {
      var decoded_jwt = jwt_decode(jwt),
          user_id,
          org_slug,
          board_slug;
      if (jwt_decode &&  decoded_jwt) {
        user_id = decoded_jwt["user-id"];
        if ( user_id ) {
          org_slug = getCookie(cookieName("last-org-" + user_id))
          if ( org_slug ) {
            board_slug = getCookie(cookieName("last-board-" + user_id + "-" + org_slug))
            if ( board_slug ){
              if ( getCookie(cookieName("last-filter-" + user_id + "-" + board_slug + "-" + org_slug)) === "by-topic" ) {
                url = "/" + org_slug + "/" + board_slug + "/by-topic";
              } else {
                url = "/" + org_slug + "/" + board_slug;
              }
            } else {
              url = "/" + org_slug;
            }
          }
        }
      }
    }
    $("#site-header-signup-item").attr("onClick", "window.location = \"" + url + "\";");

  }else{ // No logged in user
    // Show Get started for free button linked to signup with Slack
    $("#get-started-centred-bt").text( "Get started for free" );
    $("#get-started-centred-bt").attr("onClick", "window.location = \"/login?slack\"");
    // Top right corner button
    $("#site-header-signup-item").text("Get Started");
    $("#site-header-signup-item").attr("onClick", "window.location = \"/login?slack\"");
  }

});

function getParameterByName(name, url) {
  if (!url)
    url = window.location.href;
  name = name.replace(/[\[\]]/g, "\\$&");
  var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)"),
      results = regex.exec(url);
  if (!results)
    return null;
  if (!results[2])
    return '';
  return decodeURIComponent(results[2].replace(/\+/g, " "));
}

function isSafari(){
  var ua = navigator.userAgent.toLowerCase(); 
  if (ua.indexOf('safari') != -1) { 
    if (ua.indexOf('chrome') > -1) {
      return false;
    } else {
      return true;
    }
  }
}

function isIE(){
  if (navigator.appName == 'Microsoft Internet Explorer' ||
      !!(navigator.userAgent.match(/Trident/) || navigator.userAgent.match(/rv:11/) || navigator.userAgent.match(/Edge\/\d+/)) ||
      (typeof $.browser !== "undefined" && $.browser.msie == 1))
  {
    return true;
  }else{
    return false;
  }
}