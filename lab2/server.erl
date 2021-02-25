-module(server).
-export([start/1,stop/1,handle/2]).

-record(server_st, {
	server, % server atom
	channels % list of channels that exist
}).

init_state(ServerAtom) ->
	#server_st{
		server = ServerAtom,
		channels = []
	}.

-record(chan_st, {
	name, % server name. (do list_to_atom(name) to get atom)
	members % list of all members of a channel
}).

% Create a channel state with the initial users Pid
init_chan_st(Channel, Pid) ->
	#chan_st{
		name = Channel,
		members = [Pid]
	}.

% Start a new server process with the given name
% Do not change the signature of this function.
start(ServerAtom) ->
	% Start server as a genserver with handle/2 as it's function.
	spawn(genserver, start, [ServerAtom, init_state(ServerAtom), fun handle/2]).

% Stop the server process registered to the given name,
% together with any other associated processes
stop(ServerAtom) ->
	genserver:stop(ServerAtom).


% Main server handle for join requests.
handle(St, {join, Pid, Channel}) ->
	Channels = St#server_st.channels,
	% Check if the channel exists
	case lists:any(fun(X) -> X == Channel end, Channels) of
		false ->
			% Spawn a channel server with user Pid as a member.
			spawn(genserver, start, [list_to_atom(Channel), init_chan_st(Channel, Pid), fun chan_handle/2]),
			{reply, ok, St#server_st{ channels = Channels ++ [Channel] }};
		true ->
			% Propagate the join request to the channel
			R = genserver:request(list_to_atom(Channel), {join, Pid}),
			{reply, R, St}
	end.

% Channel server handle for join requests
chan_handle(St, {join, Pid}) ->
	Members = St#chan_st.members,
	% Check if member is already in channel
	case lists:any(fun(X) -> X == Pid end, Members) of
		true ->
			{reply, {error, user_already_joined, "User already in channel."}, St};
		false ->
			% Update state to add new user
			{reply, ok, St#chan_st{ members = Members ++ [Pid] }}
	end;

chan_handle(St, {leave, Pid}) ->
	Members = St#chan_st.members,
	% Check if member is in channel
	case lists:any(fun(X) -> X == Pid end, Members) of
		true ->
			% Update state to remove new user
			{reply, ok, St#chan_st{ members = lists:delete(Pid, Members) }};
		false ->
			{reply, {error, user_not_joined, "User is not in channel"}, St}
	end;

chan_handle(St, {message_send, Pid, Nick, Msg}) ->
	Channel = St#chan_st.name,
	Members = St#chan_st.members,
	% Check if member is in channel (and therefore is allowed to send messages)
	case lists:any(fun(X) -> X == Pid end, Members) of
		false ->
			{reply, {error, user_not_joined, "User is not in the channel."}, St};
		true ->
			% Remove user from the list of Recipients. Don't want to send to yourself.
			% Recipients = lists:delete(Pid, Members),
			% This function spawns a genserver:request(message_recieve)...
			Fun = fun(P) ->
				spawn(genserver,request, [P, {message_receive, Channel, Nick, Msg}])
			end,
			% ... for each user in Recipients
			lists:foreach(Fun, Members),
			{reply, ok, St}
	end.
