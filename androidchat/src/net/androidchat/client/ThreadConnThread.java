package net.androidchat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Message;

public class ThreadConnThread implements Runnable {

	public Socket socket;
	private String server;
	private String nick;
	private final String PREFS_NAME = "androidChatPrefs";
	SharedPreferences settings; 
	
	public ThreadConnThread(String serv, String ni, Socket s) {
		socket = s;
		server = serv;
		settings = ServiceIRCService.context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
		nick = settings.getString("irc_nickname_key", "AndroidChat");
	}

	public void disconnect() {
		try {
			socket.close();
		} catch (IOException ioe) {

		}

	}

	public void run() {
		try
		{
			socket = new Socket(server, 6667);
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		try
		{
			ServiceIRCService.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (NullPointerException npe)
		{
			npe.printStackTrace();
			
			// socket was null. that's odd.
		}
		try
		{
			ServiceIRCService.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (NullPointerException npe)
		{
			ServiceIRCService.channels.get("~status").addLine("*** Unable to connect (null socket)");
			if(ServiceIRCService.ChannelViewHandler != null)
				Message.obtain(ServiceIRCService.ChannelViewHandler,ServiceIRCService.MSG_UPDATECHAN, "~status").sendToTarget();

			npe.printStackTrace();
			
		}
		
		ServiceIRCService.state = 1; // logging in
		
		try
		{
			ServiceIRCService.writer.write("NICK " + nick + "\r\n");
			ServiceIRCService.writer.write("USER " + nick + " 8 * : Android Chat Client\r\n");
			ServiceIRCService.writer.flush();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		
		String line = null;
		try
		{
			while ((line = ServiceIRCService.reader.readLine()) != null)
			{
				if (line.indexOf("004") >= 0)
				{
					ServiceIRCService.state = 2;// logged in
					break;
				} else if (line.indexOf("433") >= 0)
				{
					ServiceIRCService.state = 1;// nick in use
					nick = nick + "-";
					ServiceIRCService.channels.get("~status").addLine("*** Nickname in use, attempting alternate (" + nick + ")");
					ServiceIRCService.writer.write("NICK " + nick + "\n"); // should prompt
					if(ServiceIRCService.ChannelViewHandler != null)
						Message.obtain(ServiceIRCService.ChannelViewHandler,ServiceIRCService.MSG_UPDATECHAN, "~status").sendToTarget();
					break;
				} else if (line.indexOf("432") >= 0)
				{
					ServiceIRCService.state = -1;// bad nickname
					ServiceIRCService.channels.get("~status").addLine("*** Erroneous Nickname!");
					ServiceIRCService.channels.get("~status").addLine("*** You MUST fix your nickname in Options!");
					if(ServiceIRCService.ChannelViewHandler != null)
						Message.obtain(ServiceIRCService.ChannelViewHandler,ServiceIRCService.MSG_UPDATECHAN, "~status").sendToTarget();
					return;
				} else if (line.startsWith("PING "))
				{
					ServiceIRCService.writer.write("PONG " + line.substring(5) + "\r\n");
					ServiceIRCService.writer.flush();
				} 
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		ServiceIRCService.state = 3;// autojoin
		try
		{
			//if the user has a password identify them.
			String password = settings.getString("irc_password_key", "");
			
			if (!password.equals("")) {
				ServiceIRCService.writer.write("PRIVMSG NickServ :identify " + password);
				ServiceIRCService.writer.flush();
			}
			
			String defchan = settings.getString("autoJoin", "");
			String[] autojoin = defchan.split(" ");
			for (String s : autojoin)
				ServiceIRCService.writer.write("JOIN " + s + "\n");
			ServiceIRCService.writer.flush();

		} catch (IOException e)
		{
			e.printStackTrace();
		}
		
		
		ServiceIRCService.state = 10; // connected and handling
		

		
		try
		{
			while ((line = ServiceIRCService.reader.readLine()) != null)
			{
				if (line.startsWith("PING "))
				{
					ServiceIRCService.writer.write("PONG " + line.substring(5) + "\r\n");
					ServiceIRCService.writer.flush();
				} else
				{
					// handle incoming text here
					ServiceIRCService.GetLine(line);
				}
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		} 
	}
}
