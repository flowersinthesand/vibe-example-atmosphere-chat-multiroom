$(function () {
    "use strict";

    var header = $('#header');
    var rooms = $('#rooms');
    var content = $('#content');
    var users = $('#users');
    var input = $('#input');
    var status = $('#status');
    var myName = false;
    var author = null;
    var logged = false;
    var socket = vibe;
    var subSocket;
    var connected = false;

    header.html($('<h3>', {text: 'Atmosphere Chat.'}));
    status.text('Choose chatroom:');
    input.removeAttr('disabled').focus();

    input.keydown(function (e) {
        if (e.keyCode === 13) {
            var msg = $(this).val();

            $(this).val('');
            if (!connected) {
                connected = true;
                connect(msg);
                return;
            }

            // First message received is always the author's name
            if (author == null) {
                author = msg;
                subSocket.send("init", author);
            } else if (msg.indexOf(":") !== -1) { // Private message
                var a = msg.split(":")[0];
                subSocket.send("private", {user: a, message: msg});
            } else {
                subSocket.send("public", {author: author, message: msg});
            }

            if (myName === false) {
                myName = msg;
            }
        }
    });

    function connect(chatroom) {
        function onmessage(json) {
            input.removeAttr('disabled').focus();
            if (json.rooms) {
                rooms.html($('<h2>', { text: 'Current room: ' + chatroom}));

                var r = 'Available rooms: ';
                for (var i = 0; i < json.rooms.length; i++) {
                    r += json.rooms[i] + "  ";
                }
                rooms.append($('<h3>', { text: r }))
            }

            if (json.users) {
                var r = 'Connected users: ';
                for (var i = 0; i < json.users.length; i++) {
                    r += json.users[i] + "  ";
                }
                users.html($('<h3>', { text: r }))
            }

            if (json.author) {
                if (!logged && myName) {
                    logged = true;
                    status.text(myName + ': ').css('color', 'blue');
                } else {
                    var me = json.author == author;
                    var date = typeof(json.time) == 'string' ? parseInt(json.time) : json.time;
                    addMessage(json.author, json.message, me ? 'blue' : 'black', new Date(date));
                }
            }
        }
        
        subSocket = vibe.open("/chat?room=" + encodeURIComponent(chatroom), {heartbeat: true})
        .on("open", function() {
            content.html($('<p>', {text: 'Vibe connected'}));
            input.removeAttr('disabled').focus();
        })
        .once("open", function() {
            status.text('Choose name:');
        })
        .on("entered", onmessage)
        .on("private", onmessage)
        .on("public", onmessage)
        .on("close", function(reason) {
            content.html($('<p>', { text: 'Sorry, but connection is closed due to ' + reason}));
            logged = false;
        })
        .on("waiting", function(delay, attempts) {
            content.html($('<p>', { text: 'Connection lost, trying to reconnect. Trying to reconnect in ' + delay}));
            input.attr('disabled', 'disabled');
        });
    }

    function addMessage(author, message, color, datetime) {
        content.append('<p><span style="color:' + color + '">' + author + '</span> @ ' + +(datetime.getHours() < 10 ? '0' + datetime.getHours() : datetime.getHours()) + ':'
            + (datetime.getMinutes() < 10 ? '0' + datetime.getMinutes() : datetime.getMinutes())
            + ': ' + message + '</p>');
    }
});
