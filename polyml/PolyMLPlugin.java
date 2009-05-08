/*
 *  ------------------------------------------------------------------------------------
 */

package polyml;

import java.io.IOException;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EBPlugin;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.Mode;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.ViewUpdate;

import pushstream.PushStream;
import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import java.util.regex.Pattern;

/**
 * Plugin class the PolyML Plugin for jedit.
 *
 * @author     Lucas Dixon
 * @created    03 November 2007
 * @version    0.2.0
 */
public class PolyMLPlugin extends EBPlugin {
	
	public static final String NAME = "PolyML Plugin";
	public static final String OPTION_PREFIX = "options.polyml.";

	public static final String PROPS_POLY_IDE_COMMAND = "options.polyml.polyide_command";
	public static final String PROPS_SHELL_COMMAND = "options.polyml.shell_command";
	public static final String PROPS_SHELL_PROMPT = "options.polyml.shell_prompt";
	public static final String PROPS_SHELL_MAX_HISTORY = "options.polyml.max_history";
	public static final String PROPS_COPY_OUTPUT_TO_DEBUG_BUFFER = "options.polyml.copy_output_to_debug_buffer";
	
	
	/** Associates Buffers to Processes that output to the buffer */
	static Map<Buffer,ShellBuffer> shells;
	static DefaultErrorSource errorSource;
	static PolyMLProcess polyMLProcess;
	static BufferEditor debugBuffer;
	
	public PolyMLPlugin() {
		super();
		shells = new Hashtable<Buffer, ShellBuffer>();
		errorSource = null;
		polyMLProcess = null;
		debugBuffer = null;
		System.err.println("PolyMLPlugin: started!");
	}
	
	/** 
	 * Safe get ShellBuffer of Buffer 
	 */
	public static ShellBuffer shellBufferOfBuffer(Buffer b) {
		if(b == null) { return null; } else { return shells.get(b); }
	}
	
	public static void debugMessage(String s){
		if(Boolean.parseBoolean(jEdit.getProperty(PROPS_COPY_OUTPUT_TO_DEBUG_BUFFER))) {			
			if(debugBuffer == null){ debugBuffer = new BufferEditor(); }
			debugBuffer.append(s);
		}
	}
	
	public static List<String> getPolyIDECmd(){
		String s = jEdit.getProperty(PROPS_POLY_IDE_COMMAND);
		List<String> cmd = new LinkedList<String>();
		for(String s2 : s.split(" ")){ cmd.add(s2); }
		return cmd;
	}
	
	public static String getPolyIDECmdString(){
		return jEdit.getProperty(PROPS_POLY_IDE_COMMAND);
	}
	
	// called when plugin is loaded/added
	public void start() {
		System.err.println("PolyMLPlugin: start called.");
		errorSource = new DefaultErrorSource(NAME);
		DefaultErrorSource.registerErrorSource(errorSource);
		restartPolyML();
	}
	
	// called when plugin is un-loaded/removed
	public void stop() {
		stopAllShellBuffers(); 
		DefaultErrorSource.unregisterErrorSource(errorSource);
		if(polyMLProcess != null) { polyMLProcess.closeProcess(); }
	}
	
	static public boolean restartPolyML() {
		System.err.println("restarting polyml...");
		try { 
			List<String> cmd = getPolyIDECmd();
			if(polyMLProcess == null) { 
				polyMLProcess = new PolyMLProcess(cmd, errorSource);
			} else {
				polyMLProcess.restartProcessFromCmd(cmd);
			}
			return true;
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("PolyMLPlugin: Failed to restart PolyML!");
			polyMLProcess = null;
			return false;
		}
	}
	
	/** 
	 * send buffer to ML and process contents
	 * @param b
	 */
	static public void sendBufferToPolyML(Buffer b) {
		polyMLProcess.compileBuffer(b);
	}

	static public BufferEditor newDebugShellBuffer() {
		debugBuffer = new BufferEditor();
		return debugBuffer;
	}
	
	
	static public ShellBuffer newShellBuffer() {
		Buffer b = jEdit.newFile(null, jEdit.getFirstView().getBuffer().getDirectory());
		//System.err.println("newShellBuffer");

		ShellBuffer s;
		try {
			s = new ShellBuffer(new BufferEditor(b));
			shells.put(b, s);
			// show buffer after adding to shell list so that buffer 
			// changed events trigger use of text area extensions. 
			View v = jEdit.getFirstView();
			v.showBuffer(b);
			s.showInTextArea(v.getTextArea());
			return s;
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(e.toString());
			return null;
		}
	}
	
	
	static public void processShellBufferToEOF(Buffer b) {
		try {
			//System.err.println("called processShellBufferToEOF");
			ShellBuffer s = shellBufferOfBuffer(b);
			if(s == null) {
				System.err.println("Not a ShellBuffer!");
			} else {
				s.sendBufferTextToEOF();
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(e.toString());
		}
	}
	
	
	/* start and restart are the same: they restart shell in the current buffer */
	static public void startShellInBuffer(Buffer b) {
		//System.err.println("startShellInBuffer");
		restartShellInBuffer(b);
	}
	
	
	static public void prevCommand(Buffer b) {
		ShellBuffer sb = shellBufferOfBuffer(b);
		if(sb != null){
			sb.prevCommand();
		}
	}
	
	static public void nextCommand(Buffer b) {
		ShellBuffer sb = shellBufferOfBuffer(b);
		if(sb != null){
			sb.nextCommand();
		}
	}
	
	
	static public void restartShellInBuffer(Buffer b) {
		//System.err.println("restartShellInBuffer");
		ShellBuffer sb = shellBufferOfBuffer(b);
		if(sb != null){
			try {
				sb.restartProcess();
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println(e.toString());
			}
		} else {
			try {
				ShellBuffer s =  new ShellBuffer(new BufferEditor(b));
				shells.put(b,s);
				// turn on extra text area extensions for all views of this shell buffer
				s.showInAllTextAreas();
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println(e.toString());
			}
		}
	}
	
	static public void stopShellInBuffer(Buffer b) {
		ShellBuffer sb = shellBufferOfBuffer(b);
		if(sb != null){
			sb.stopProcess();
		}
	}
	
	static public void stopAllShellBuffers() {
		for(ShellBuffer sb : shells.values()) {
			sb.stopProcess();
		}
	}
	
	/**
	 * when an edit pane shows a ShellBuffer, add the shell Buffer's TextAreaExtension. 
	 * @param editPane
	 */
	public void usingShellBufferTextArea(EditPane editPane) {
		Buffer b = editPane.getBuffer();
		ShellBuffer s = shellBufferOfBuffer(b);
		System.err.println("usingShellBufferTextArea");
		if(s != null) {
			s.showInTextArea(editPane.getTextArea());
		}
	}
	
	/**
	 * when an edit pane stops showing a ShellBuffer, remove the shell Buffer's TextAreaExtension. 
	 * @param editPane
	 */
	public void unusingShellBufferTextArea(EditPane editPane) {
		Buffer b = editPane.getBuffer();
		ShellBuffer s = shellBufferOfBuffer(b);
		if(s != null) {
			s.unShowInTextArea(editPane.getTextArea());
		}
	}
	
	
	/** handle buffer closing events to close associated process. */
	public void handleMessage(EBMessage msg){
		if(shells == null) { return; }
		if(msg instanceof BufferUpdate) {
			BufferUpdate bufferUpdate = (BufferUpdate)msg;
			// if a buffer is closed; close its associated shell
			if(bufferUpdate.getWhat() == BufferUpdate.CLOSING) {
				Buffer b = bufferUpdate.getBuffer();
				ShellBuffer s = shellBufferOfBuffer(b);
				if(s != null){
					s.stopProcess(); // note: this unshows text area extensions too
					shells.remove(b);
				}
				if(debugBuffer != null && b == debugBuffer.mBuffer) { debugBuffer = null; }
			} 
			//else if(bufferUpdate.getWhat() == BufferUpdate.SAVED) {
				// Dummy stub: maybe do something when PolyML buffer is saved
				//Buffer b = bufferUpdate.getBuffer();
				//Mode m = b.getMode();
				//if(m.getName() == "PolyML Mode") { // Hacky: better way to check mode? 
				//	System.err.println("PolyML Mode Buffer Saved! do something? ");
				//}
			//}
		} else if(msg instanceof EditPaneUpdate) {
			// handle creation/changing of shell buffers: add on extra painting extension when needed. 
			EditPaneUpdate editPaneUpdate = (EditPaneUpdate)msg;
			if(editPaneUpdate.getWhat() == EditPaneUpdate.CREATED) {
				usingShellBufferTextArea(editPaneUpdate.getEditPane());
			} else if(editPaneUpdate.getWhat() == EditPaneUpdate.BUFFER_CHANGING) {
				unusingShellBufferTextArea(editPaneUpdate.getEditPane());
			} else if(editPaneUpdate.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
				usingShellBufferTextArea(editPaneUpdate.getEditPane());
			} else if(editPaneUpdate.getWhat() == EditPaneUpdate.DESTROYED) {
				unusingShellBufferTextArea(editPaneUpdate.getEditPane());
			}
		}
	}
}
