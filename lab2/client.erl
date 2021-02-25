-module(client).
-export([handle/2, initial_state/3]).

% This record defines the structure of the state of a client.
% Add whatever other fields you need.
-record(client_st, {
    gui, % atom of the GUI process
    nick, % nick/username of the client
    server % atom of the chat server
}).

% Return an initial state record. This is called from GUI.
% Do not change the signature of this function.
initial_state(Nick, GUIAtom, ServerAtom) ->
    #client_st{
        gui = GUIAtom,
        nick = Nick,
        server = ServerAtom
    }.

% handle/2 handles each kind of request from GUI
% Parameters:
%   - the current state of the client (St)
%   - request data from GUI
% Must return a tuple {reply, Data, NewState}, where:
%   - Data is what is sent to GUI, either the atom `ok` or a tuple {error, Atom, "Error message"}
%   - NewState is the updated state of the client

% Join channel
handle(St, {join, Channel}) ->
 	Server = St#client_st.server,
	% Check if the main server is down
	case whereis(Server) of
		undefined ->
			{reply, {error, server_not_reached, "Server unresponsive"}, St};
		_Else ->
			% If not, send the join request to the main server.
			R = genserver:request(Server, {join, self(), Channel}),
			{reply, R, St}
	end; 
% Leave channel
handle(St, {leave, Channel}) ->
	Server = list_to_atom(Channel),
	% Check if the channel server is down
	case whereis(Server) of
		undefined ->
			{reply, {error, server_not_reached, "Server unresponsive"}, St};
		_Else ->
			% If not, send the leave request to the Channel server.
			R = genserver:request(Server, {leave, self()}),
			{reply, R, St}
	end;
   
% Sending message (from GUI, to channel)
handle(St, {message_send, Channel, Msg}) ->
 	Server = list_to_atom(Channel),
	% Check if the channel server is down
	case whereis(Server) of
		undefined ->
			{reply, {error, server_not_reached, "Server unresponsive"}, St};
		_Else ->
			% If not, send the message_send request to the Channel server
			R = genserver:request(Server, {message_send, self(), St#client_st.nick, Msg}),
			{reply, R, St}
	end;
   
% This case is only relevant for the distinction assignment!
% Change nick (no check, local only)
handle(St, {nick, NewNick}) ->
    {reply, ok, St#client_st{nick = NewNick}} ;

% ---------------------------------------------------------------------------
% The cases below do not need to be changed...
% But you should understand how they work!

% Get current nick
handle(St, whoami) ->
    {reply, St#client_st.nick, St} ;

% Incoming message (from channel, to GUI)
handle(St = #client_st{gui = GUI}, {message_receive, Channel, Nick, Msg}) ->
    gen_server:call(GUI, {message_receive, Channel, Nick++"> "++Msg}),
    {reply, ok, St} ;

% Quit client via GUI
handle(St, quit) ->
    % Any cleanup should happen here, but this is optional
    {reply, ok, St} ;

% Catch-all for any unhandled requests
handle(St, Data) ->
    {reply, {error, not_implemented, "Client does not handle this command"}, St} .
