package net.androidchat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.util.Log;

public class ServiceIRCService extends Service {
	public static Context context;
	private static Thread connection;
	private static Thread updates;
 
	private static Socket socket;
	public static BufferedWriter writer;
	public static BufferedReader reader;
	public static int state;
	private static String server = "irc.freenode.net";// "38.100.42.254"; //
	public static String nick = "AndroidChat";

	public static final int MSG_UPDATECHAN = 0;
	public static final int MSG_CLEARPROGRESS = 1;
	public static final int MSG_CHANGECHAN = 2;
	public static final int MSG_DISCONNECT = 3;
	public static final int MSG_CHANGEWINDOW = 4;

	public static final String AC_VERSION = "1.02b";

	private static boolean is_first_list;

	public static HashMap<String, ClassChannelContainer> channels;
	public static HashMap<String, ClassChannelDescriptor> channel_list;
	public static HashMap<String, Location> temp_user_locs;

	public static Handler ChannelViewHandler;

	public static String lastwindow = "~status";
	public static String curwindow = "~status";
	

	public static Boolean shownChanListConnect = false;
	
	
	public static void SetViewHandler(Handler what)
	{
		ChannelViewHandler = what;
		Message.obtain(ServiceIRCService.ChannelViewHandler,
				ServiceIRCService.MSG_CHANGEWINDOW, ServiceIRCService.curwindow)
				.sendToTarget();
		
	}
	
	// this is epic irc parsing.
	public static void GetLine(String line) {
		// rfc 2812
		// [:prefix] command|numeric [arg1, arg2...] :extargs

		//Log.d("ServiceIRCService", "raw line: " + line);

		String args, prefix, command;
		args = prefix = command = "";

		//System.out.println("debug: " + line);

		boolean flagupdate = false;
		String updatechan = "";

		// pull off extended arguments first
		if (line.indexOf(":", 2) != -1)
			args = line.substring(line.indexOf(":", 2) + 1).trim();

		// if we have extended arguments, remove them from the parsing
		if (args.length() > 0)
			line = line.substring(0, line.length() - args.length());

		String[] toks = line.split(" "); // split by spaces.

		if (toks[0].startsWith(":")) // we have a prefix
		{
			prefix = toks[0].substring(1);
			command = toks[1];
		} else {
			prefix = null;
			command = toks[0];
		}

		if (command.equals("641"))
		// :servername 641 yournick #channel lat long
		{
			ClassChannelContainer temp;
			String chan = toks[3].toLowerCase();
			if (channels.containsKey(chan)) {
				temp = channels.get(chan);
				temp.loc_lat = Float.parseFloat(toks[4]);
				temp.loc_lng = Float.parseFloat(toks[5]);
			}

			if (channel_list.containsKey(chan)) {
				ClassChannelDescriptor t = channel_list.get(chan);
				t.loc_lat = Float.parseFloat(toks[4]);
				t.loc_lng = Float.parseFloat(toks[5]);
			}
		} else if (command.equals("640")) // user location numeric
		{	
			//>> :irc.androidchat.com 640 Kuja poffy -0 -0
			
			//Location l = new Location();
			//l.setLatitude(Float.parseFloat(toks[4]));
			//l.setLongitude(Float.parseFloat(toks[5]));
			
			if(temp_user_locs.containsKey(toks[3].toLowerCase()))
			{
				//temp_user_locs.get(toks[3].toLowerCase()).setLatitude(l.getLatitude());
				//temp_user_locs.get(toks[3].toLowerCase()).setLongitude(l.getLongitude());
			}			
			//else
				//temp_user_locs.put(toks[3].toLowerCase(), l);			
			
		} else if (command.equals("323")) // list end numeric
		{
			is_first_list = true;
		} else if (command.equals("322")) // list numeric
		{ // 0 1 2 3 4 args
			// :servername 322 yournick <channel> <#_visible> :<topic>
			// :irc.androidchat.com 322 AndroidChat #hi 3 :[+nt] SVN update for
			// MASSIVE UI DAMAGE. Like, it's actually approaching usable now. I
			// changed a LOT of behavior. I need you to create an options
			// activity, and then I think we're nearly done as far as the client
			// is concerned.

			if (is_first_list) {
				channel_list.clear();
				is_first_list = false;
			}
			/*
			 * if (channel_list.containsKey(toks[2])) { ClassChannelDescriptor t =
			 * channel_list.get(toks[2]); t.channame = toks[2]; t.chantopic =
			 * args; t.chatters = Integer.parseInt(toks[3]);
			 * 
			 * RequestChannelLocation(toks[2]); } else {
			 */
			ClassChannelDescriptor t = new ClassChannelDescriptor();
			try {
			
			t.channame = toks[3];
			t.chantopic = args.substring(args.indexOf("]") + 1).trim();
			t.chatters = Integer.parseInt(toks[4]);
		
			} catch (ArrayIndexOutOfBoundsException aioobe)
			{
				
			} 
			channel_list.put(toks[3], t);
			//RequestChanLocation(toks[3]);
			// }
		} else if (command.equals("TOPIC")) {
			//> :Kuja!Kuja@AFCBE3.FDA6AD.34D090.09AED6 TOPIC #hi :Welcome to AndroidChat!!!
			ClassChannelContainer temp;
			String chan = toks[2].toLowerCase();
			if (channels.containsKey(chan)) {
				temp = channels.get(chan);
				temp.addLine("*** Topic for " + toks[2] + " is now: " + args);
				temp.chantopic = args;
				flagupdate = true;
				updatechan = chan;
			} // ignore topics for channels we aren't in
		} else if (command.equals("331") || command.equals("332")) // topic
		// numeric
		// :servername 331 yournick #channel :no topic
		// :servername 332 yournick #channel :topic here
		{
			ClassChannelContainer temp;
			String chan = toks[3].toLowerCase();
			if (channels.containsKey(chan)) {
				temp = channels.get(chan);
				temp.addLine("*** Topic for " + toks[3] + " is: " + args);
				temp.chantopic = args;
				flagupdate = true;
				updatechan = chan;
			} // ignore topics for channels we aren't in

		} else if (command.equals("372")) {
			if (ChannelViewHandler != null) {
				channels.get("~status").addLine(args);
				Message.obtain(ChannelViewHandler,
						ServiceIRCService.MSG_UPDATECHAN, "~status")
						.sendToTarget();
			}
		} else if (command.equals("353")) {
			// :dexter.chatspike.net 353 yournick = #funfactory :chattie dizz
			// @+takshaka
			// poshgal @LDI Aldebaran Sweet2 James54
			if (channels.containsKey(toks[4].toLowerCase())) {
				String[] incoming = args.split(" ");
				ClassChannelContainer c = channels.get(toks[4].toLowerCase());
				if (c.NAMES_END == true) {
					c.NAMES_END = false;
					c.chanusers.clear();
				}

				for (String s : incoming) {
					c.chanusers.add(s.trim());
				}

			}
		} else if (command.equals("366")) {
			// :dexter.chatspike.net 366 t #testtest :End of /NAMES list.
			String chan = toks[3].toLowerCase();
			if (channels.containsKey(chan)) {
				channels.get(chan).NAMES_END = true;

				StringBuilder sb = new StringBuilder();

				sb.append("*** Users on ").append(toks[3]).append(": ");
				for (String s : channels.get(chan).chanusers) {
					sb.append(s).append(" ");
				}

				channels.get(chan).addLine(sb.toString().trim());

				flagupdate = true;
				updatechan = chan;
			}
		} else if (command.equals("NICK")) {
			// :nick!mask@mask NICK newnick
			// :Testing!AndroidChat@71.61.229.105 NICK Poop

			String oldnick = toks[0].substring(1, toks[0].indexOf("!"));
			if (nick.toLowerCase().equals(oldnick.toLowerCase())) // we
																	// changed
			// /our/ nickname
			{
				nick = toks[2];
			}

			for (ClassChannelContainer c : channels.values()) {
				for (String s : c.chanusers) {
					if (s.toLowerCase().equals(oldnick.toLowerCase())) {
						c.chanusers.remove(s);
						c.chanusers.add(toks[2]);
						c.addLine("*** " + oldnick + " is now known as "
								+ toks[2]);
						if (ChannelViewHandler != null)
							Message.obtain(ChannelViewHandler,
									ServiceIRCService.MSG_UPDATECHAN,
									c.channame.toLowerCase()).sendToTarget();
						break;
					}
				}
			}
			// todo: notify of nick renames here
		} else if (command.equals("QUIT")) {
			// :Jeff!Kuja@71.61.229.105 QUIT :
			String whoquit = toks[0].substring(1, toks[0].indexOf("!"));

			for (ClassChannelContainer c : channels.values()) {
				for (String s : c.chanusers) {
					if (s.toLowerCase().equals(whoquit.toLowerCase())) {
						c.chanusers.remove(s);
						c.addLine("*** " + whoquit + " has disconnected ("
								+ args + ")");
						if (ChannelViewHandler != null)
							Message.obtain(ChannelViewHandler,
									ServiceIRCService.MSG_UPDATECHAN,
									c.channame.toLowerCase()).sendToTarget();
						break;
					}
				}
			}

		} else if (command.equals("KICK")) {
			// :prefix kick #chan who :why
			if (channels.containsKey(toks[2].toLowerCase())) {
				ClassChannelContainer c = channels.get(toks[2].toLowerCase());
				if (c.chanusers.contains(toks[3]))
					c.chanusers.remove(toks[3]);
				c.addLine("*** " + toks[3] + " was kicked (" + args + ")");
				flagupdate = true;
				updatechan = toks[2];
			}
		} else if (command.equals("PART"))
		// User must have left a channel
		{
			// :Kraln!Kraln@71.61.229.105 PART #hi
			String who = toks[0].substring(1, toks[0].indexOf("!"));
			ClassChannelContainer temp;
			if (who.equals(nick)) // if we joined a channel
			{

				if (channels.containsKey(toks[2].toLowerCase())) // existing
				// channel?
				{
					
					temp = channels.get(toks[2].toLowerCase());
					temp.addLine("*** You have left this channel.");

					if (ChannelViewHandler != null) {
						Message.obtain(ChannelViewHandler,
								ServiceIRCService.MSG_UPDATECHAN,
								temp.channame.toLowerCase()).sendToTarget();
						
					
						Message.obtain(ChannelViewHandler,
								ServiceIRCService.MSG_CHANGEWINDOW, lastwindow)
								.sendToTarget();
	
						lastwindow = "~status";
					}
					
					channels.remove(temp.channame.toLowerCase()); // it will now
					flagupdate = false;
					updatechan = "~status";
				}
			} else {
				temp = channels.get(toks[2].toLowerCase());
				temp.chanusers.remove(who);
				temp.addLine("*** " + who + " has left the channel.");
				flagupdate = true;
				updatechan = toks[2].toLowerCase();
			}
			
		} else if (command.equals("JOIN"))
		// User must have joined a channel
		{
			String who = toks[0].substring(1, toks[0].indexOf("!"));
			ClassChannelContainer temp;
			Log.d("IRC - Debug", "******* Nick is " + nick + " *************");
			if (who.equals(nick)) // if we joined a channel
			{

				if (channels.containsKey(args.toLowerCase())) // existing
																// channel?
				{
					temp = channels.get(args.toLowerCase());
				} else {
					temp = new ClassChannelContainer();
					temp.channame = args.toLowerCase();
					temp.addLine("*** Now talking on " + args + "...");
					channels.put(args.toLowerCase(), temp);
					if (ChannelViewHandler != null)
					{
						Message.obtain(ChannelViewHandler,
								ServiceIRCService.MSG_CHANGEWINDOW,
								args.toLowerCase()).sendToTarget();
						Message.obtain(ChannelViewHandler,
								ServiceIRCService.MSG_UPDATECHAN,
								args.toLowerCase()).sendToTarget();
				
					}
				}
			} else {
				if (args.equals("")) {
					temp = channels.get(toks[2].toLowerCase());
				} else {
					temp = channels.get(args.toLowerCase());
				}
				temp.chanusers.add(who);
				temp.addLine("*** " + who + " has joined the channel.");
			}
			flagupdate = true;
			updatechan = args.toLowerCase();

		} else if (command.equals("PRIVMSG"))
		// to a channel?
		{
			ClassChannelContainer temp;
			String chan = toks[2].toLowerCase();

			if (channels.containsKey(chan)) // existing channel?
			{
				temp = channels.get(chan);
				if (args.trim().startsWith("ACTION")) {
					/* temp.addLine("* "
							+ toks[0].substring(1, toks[0].indexOf("!")) + " "
							+ args.substring(7));*/
					temp.addLine("***" + toks[0].substring(1, toks[0].indexOf("!")) + "~+" + args);
				} else
					/*temp.addLine("<"
							+ toks[0].substring(1, toks[0].indexOf("!")) + "> "
							+ args);*/
					temp.addLine(toks[0].substring(1, toks[0].indexOf("!")) + "~+" + args);
				
				flagupdate = true;
				updatechan = chan;
			} else if (chan.equals(nick.toLowerCase())) // crap, it's a private
			// message
			{ // :Kraln!Kuja@71.61.229.105 PRIVMSG AndroidChat2 :Hello

				String who = toks[0].substring(1, toks[0].indexOf("!"));
				if (channels.containsKey(who.toLowerCase())) // existing pm
				{
					temp = channels.get(who.toLowerCase());
				} else {
					temp = new ClassChannelContainer();
					temp.channame = who;
					temp.addLine("*** Now talking with " + who + "...");
					temp.IS_PM = true;
					channels.put(who.toLowerCase(), temp);
					if(context.getSharedPreferences("androidChatPrefs", 0).getBoolean("pmAlert", true))
					{ if (ChannelViewHandler != null)  {
						Message.obtain(ChannelViewHandler,
								ServiceIRCService.MSG_CHANGEWINDOW,
								who.toLowerCase()).sendToTarget();
						if (ChannelViewHandler != null) 
							Message.obtain(ChannelViewHandler,
									ServiceIRCService.MSG_UPDATECHAN,
									who.toLowerCase()).sendToTarget();
						
					} } else 
						if (ChannelViewHandler != null) 
						Message.obtain(ChannelViewHandler,
								ServiceIRCService.MSG_UPDATECHAN,
								who.toLowerCase()).sendToTarget();
				}
				temp.addLine("<" + who + "> " + args);
				/*mNM.notify(R.string.irc_started, new Notification(context,
						R.drawable.mini_icon, context
								.getText(R.string.ui_newpm), System
								.currentTimeMillis(),
						"AndroidChat - Notification", context
								.getText(R.string.ui_newpm), null,
						R.drawable.mini_icon, "Android Chat", null));*/
				
				flagupdate = true;
				updatechan = who.toLowerCase();
			}
		}

		if (flagupdate) {
			if (ChannelViewHandler != null)
				Message.obtain(ChannelViewHandler,
						ServiceIRCService.MSG_UPDATECHAN,
						updatechan.toLowerCase()).sendToTarget();
		}

	}

	public static void JoinChan(String channel) {
		try {
			String temp = "JOIN " + channel + "\n";
			writer.write(temp);
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException npe) {
			npe.printStackTrace();
		}
	}

	/*public static void AskForChannelList() {

		try {
			String temp = "LIST\n";
			writer.write(temp);
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException npe) {
			npe.printStackTrace();
		}
	}*/

	public static void QuitServer() {
		try {
			String temp = "QUIT :AndroidChat client has quit\n";
			writer.write(temp);
			writer.flush();
			ServiceIRCService.state = -1;
			ServiceIRCService.reader.close();
			ServiceIRCService.connection.interrupt();
			//mNM.cancel(R.string.irc_started);
			((Service)context).stopSelf();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException npe) {
			npe.printStackTrace();
		}

	}
	
	public static void OpenPMWindow(String who) {
		ClassChannelContainer temp;

	
		if (channels.containsKey(who.toLowerCase())) // existing pm
		{
			if (ChannelViewHandler != null)  {
				Message.obtain(ChannelViewHandler,
						ServiceIRCService.MSG_CHANGEWINDOW,
						who.toLowerCase()).sendToTarget();
				
				
			}
		} else {
			temp = new ClassChannelContainer();
			temp.channame = who;
			temp.addLine("*** Now talking with " + who + "...");
			temp.IS_PM = true;
			channels.put(who.toLowerCase(), temp);
			if (ChannelViewHandler != null)  {
				Message.obtain(ChannelViewHandler,
						ServiceIRCService.MSG_CHANGEWINDOW,
						who.toLowerCase()).sendToTarget();
				
			
			}
		}
	}

	public static void SendToChan(String chan, String what) {
		if (what.trim().equals(""))
			return;

		if (what.startsWith("/")) {
			// this is a raw command.
			// parse here for intelligent commands, otherwise send it along raw

			if (what.startsWith("/me ")) // special case...
			{
				try {
					String temp = "PRIVMSG " + chan + " :" + '\001' + "ACTION "
							+ what.substring(4) + '\001' + "\n";

					writer.write(temp);
					writer.flush();
					GetLine(":" + nick + "! " + temp);

				} catch (IOException e) {
					e.printStackTrace();
				} catch (NullPointerException npe) {
					npe.printStackTrace();
				}
				if (ChannelViewHandler != null)
					Message.obtain(ChannelViewHandler,
							ServiceIRCService.MSG_UPDATECHAN, chan)
							.sendToTarget();
				return;
			}

			if (what.equals("/close")) // special case...
			{

				if (channels.get(chan).IS_PM) {
					if (ChannelViewHandler != null) {
						Message.obtain(ChannelViewHandler,
								ServiceIRCService.MSG_CHANGEWINDOW, lastwindow)
								.sendToTarget();
						lastwindow = "~status";

					}

					channels.remove(chan);

				} else if (channels.get(chan).IS_STATUS) {
					// do nothing
				} else {
					// send part
					try {
						String temp = "PART " + chan + " :User closed window\n";
						writer.write(temp);
						writer.flush();
						if (ChannelViewHandler != null) {
							
							Message.obtain(ChannelViewHandler,
									ServiceIRCService.MSG_CHANGEWINDOW, lastwindow)
									.sendToTarget();

							lastwindow = "~status";

						}
						channels.remove(chan);

					} catch (IOException e) {
						e.printStackTrace();
					} catch (NullPointerException npe) {
						npe.printStackTrace();
					}
				}

				return;
			}
			if (what.startsWith("/msg ")) // special case...
			{
				try {
					String blah = what.substring(5);
					String towho = blah.substring(0, blah.indexOf(" ")).trim();
					String args = blah.substring(blah.indexOf(" ")).trim();

					String temp = "PRIVMSG " + towho + " :" + args + "\n";

					writer.write(temp);
					writer.flush();
					GetLine(":" + nick + "! " + temp);

				} catch (IOException e) {
					e.printStackTrace();
				} catch (NullPointerException npe) {
					npe.printStackTrace();
				}
				if (ChannelViewHandler != null)
					Message.obtain(ChannelViewHandler,
							ServiceIRCService.MSG_UPDATECHAN, chan)
							.sendToTarget();
				return;
			}

			try {
				String temp = what.substring(1) + "\n";
				writer.write(temp);
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException npe) {
				npe.printStackTrace();
			}
			return;

		}

		if ((chan == null) || (channels.get(chan).IS_STATUS)) {
			// error about not being on a channel here
			return;
		}
		
		

		// PRIVMSG <target> :<message>
		try {
			String temp = "PRIVMSG " + chan + " :" + what + "\n";
			GetLine(":" + nick + "! " + temp);
			writer.write(temp);
			writer.flush();
			if (ChannelViewHandler != null)
				Message.obtain(ChannelViewHandler,
						ServiceIRCService.MSG_UPDATECHAN, chan).sendToTarget();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException npe) {
			npe.printStackTrace();
		}
	}

	@Override
	public void onCreate() {
		mNM = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

		// This is who should be launched if the user selects our persistent
		// notification.
		context = this;
		Intent intent = new Intent();
		intent.setClass(this, ActivityAndroidChatMain.class);

		if (intent.hasExtra("server")) server = intent.getExtras().getString("server");
		if (intent.hasExtra("nick")) nick = intent.getExtras().getString("nick");
		 
		SharedPreferences settings = ServiceIRCService.context.getSharedPreferences("androidChatPrefs", Context.MODE_WORLD_READABLE);
		nick = settings.getString("irc_nickname_key", "AndroidChat");
		
		channels = new HashMap<String, ClassChannelContainer>();
		channel_list = new HashMap<String, ClassChannelDescriptor>();
		temp_user_locs = new HashMap<String, Location>();

		is_first_list = true;

		ClassChannelContainer debug = new ClassChannelContainer();
		debug.channame = "Status/Debug Window";
		debug.addLine("AndroidChat v" + AC_VERSION + " started.");
		debug.IS_STATUS = true;
		channels.put("~status", debug);


		
		if (ChannelViewHandler != null) {
			Message.obtain(ChannelViewHandler,
					ServiceIRCService.MSG_CHANGEWINDOW, "~status")
					.sendToTarget();
			
		}

		// Display a notification about us starting. We use both a transient
		// notification and a persistent notification in the status bar.
		/*mNM.notify(R.string.irc_started, new Notification(context,
				R.drawable.mini_icon, getText(R.string.irc_started), System
						.currentTimeMillis(), "AndroidChat - Notification",
				getText(R.string.irc_started), intent, R.drawable.mini_icon,
				"Android Chat", intent));*/

		connection = new Thread(new ThreadConnThread(server, nick, socket));
		connection.start();

		if(getSharedPreferences("androidChatPrefs", 0).getBoolean("sendLoc", true))
		{
			updates = new Thread(new ThreadUpdateLocThread(context));
			updates.start();
		}
		/*mNM.notify(R.string.irc_started, new Notification(context,
				R.drawable.mini_icon, getText(R.string.irc_connected), System
						.currentTimeMillis(), "AndroidChat - Notification",
				getText(R.string.irc_connected), null, R.drawable.mini_icon,
				"Android Chat", null));*/

	}
	
	public void disconnect() {
		this.stopSelf();
	}

	@Override
	public void onDestroy() {
		// Cancel the persistent notification.
		QuitServer();
		state = 0;
		if (ChannelViewHandler != null) {
			channels.get("~status").addLine("*** Disconnected");
			Message.obtain(ChannelViewHandler,
					ServiceIRCService.MSG_CHANGEWINDOW, "~status")
					.sendToTarget();
			
		}

		// Tell the user we stopped.
		/*mNM.notify(R.string.irc_started, new Notification(context,
				R.drawable.mini_icon, getText(R.string.irc_stopped), System
						.currentTimeMillis(), "AndroidChat - Notification",
				getText(R.string.irc_stopped), null, R.drawable.mini_icon,
				"Android Chat", null));*/
		
		//mNM.cancel(R.string.irc_started);
		
	}

	public IBinder onBind(Intent intent) {
		return getBinder();

	}

	public IBinder getBinder() {
		return mBinder;
	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new Binder() {
		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply,
				int flags) {
			try {
			return super.onTransact(code, data, reply, flags);
			} catch (Exception e) {
				return false;
			}
		}
	};

	public static NotificationManager mNM;
}
