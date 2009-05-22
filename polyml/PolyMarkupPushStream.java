package polyml;

import java.util.Iterator;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.textarea.Selection;

import pushstream.PushStream;
import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;

public class PolyMarkupPushStream implements PushStream<PolyMarkup> {

	CompileInfos compileInfos;
	DefaultErrorSource errorSource;

	public PolyMarkupPushStream(DefaultErrorSource e, CompileInfos p) {
		errorSource = e;
		compileInfos = p;
	}
	
	/**
	 * create initial location response from PolyMarkup
	 */
	class LocationResponse {
		String parseID;
		String reqID;
		int start;
		int end;
		Iterator<PolyMarkup> markup;
		
		public LocationResponse(PolyMarkup m){
			markup = m.getSubs().iterator();
			reqID = markup.next().getContent();
			parseID = markup.next().getContent();
			start = Integer.parseInt(markup.next().getContent());
			end = Integer.parseInt(markup.next().getContent());
		}
		
		public String getSrcLocTextOfBuffer(Buffer srcBuffer) {
			return srcBuffer.getText(start, end - start);
		}
		
		public String getSrcLocText() {
			CompileRequest pInfo = compileInfos.getFromParseID(parseID);
			Buffer buffer = jEdit.getBuffer(pInfo.fileName);
			return getSrcLocTextOfBuffer(buffer);
		}
	}
	
	/**
	 * initial location followed by a file and a location in that file. 
	 */
	class FullLocationResponse extends LocationResponse {
		
		String filenameLoc;
		int lineLoc; 
		int startLoc;
		int endLoc;
		
		public FullLocationResponse(PolyMarkup m){
			super(m);
			if(markup.hasNext()) {
				filenameLoc = markup.next().getContent();
				lineLoc = Integer.parseInt(markup.next().getContent());
				startLoc = Integer.parseInt(markup.next().getContent());
				endLoc = Integer.parseInt(markup.next().getContent());
			} else { // we get back null filename if not such remote location
				filenameLoc = null;
				lineLoc = 0;
				startLoc = 0;
				endLoc = 0;
			}
		}
		
		public boolean locExists() {
			return (filenameLoc != null);
		}
	}
	
	/**
	 * 
	 * @param l
	 */
	void noteLocation(FullLocationResponse l) {
		CompileRequest cr = compileInfos.getFromParseID(l.parseID);
		Buffer srcBuffer = jEdit.getBuffer(cr.fileName);
		//Buffer srcBuffer = pInfo.editPane.getBuffer();
		
		if(l.locExists()) {
			Buffer locBuffer = jEdit.getBuffer(l.filenameLoc);
			int line = locBuffer.getLineOfOffset(l.startLoc);
			int line_offset = l.startLoc - locBuffer.getLineStartOffset(line);
			int end_line = locBuffer.getLineOfOffset(l.endLoc);
			int end_offset = 0;
			if (end_line == line) {
				end_offset = l.endLoc - locBuffer.getLineStartOffset(end_line);
			}
			
			errorSource.addError(new DefaultErrorSource.DefaultError(
					errorSource, ErrorSource.WARNING, locBuffer.getPath(), line,
					line_offset, end_offset, "Location of: `" + l.getSrcLocTextOfBuffer(srcBuffer) + "`" ));
		} else {
			int line = srcBuffer.getLineOfOffset(l.start);
			int line_offset = l.start - srcBuffer.getLineStartOffset(line);
			int end_line = srcBuffer.getLineOfOffset(l.end);
			int end_offset = 0;
			if (end_line == line) {
				end_offset = l.end - srcBuffer.getLineStartOffset(end_line);
			}
			
			errorSource.addError(new DefaultErrorSource.DefaultError(
					errorSource, ErrorSource.WARNING, srcBuffer.getPath(), line,
					line_offset, end_offset, "No decloration for: `" + l.getSrcLocTextOfBuffer(srcBuffer) + "`" ));
		}
	}

	public synchronized void add(PolyMarkup m) {
		System.err.println("PolyMarkupPushStream: adding markup");
		
		PolyMLPlugin.debugMessage("\n\n"); 
		// to make output buffer more readable; add new lines after each bit of markup is successfully added. 
		
		if(m.kind == PolyMarkup.INKIND_COMPILE) {
			CompileResult r = new CompileResult(m);
			
			CompileRequest cr = compileInfos.compileCompleted(r);
			System.err.println("got compile request result");
			synchronized(cr) {
				System.err.println("notifying all");
				cr.notifyAll(); // any threads waiting on compile to be completed can wake up
				System.err.println("notified all");
			}
			String fileName = cr.fileName;
			Buffer buffer = jEdit.getBuffer(cr.fileName);
			//String fileName = i.buffer.getPath();
			//Buffer buffer = i.buffer;
			
			errorSource.removeFileErrors(fileName);
			if(r.isBug()) {
				errorSource.addError(new DefaultErrorSource.DefaultError(
						errorSource, ErrorSource.ERROR, fileName, 0,
						0, 0, "BUG: Failed to check using PolyML."));
			} else {
				for (PolyMLError e : r.errors) {
					
					System.err.println("PolyMarkupPushStream: " + e.startPos + ":" + e.endPos);
					
					int line = buffer.getLineOfOffset(e.startPos);
					int line_offset = e.startPos - buffer.getLineStartOffset(line);
					int end_line = buffer.getLineOfOffset(e.endPos);
					int end_offset = 0;
					if (end_line == line) {
						end_offset = e.endPos - buffer.getLineStartOffset(end_line);
					}
					
					int errorKind;
					if(e.kind == PolyMLError.KIND_FATAL || e.kind == PolyMLError.KIND_EXCEPTION){
						errorKind = ErrorSource.ERROR;
					} else {
						errorKind = ErrorSource.WARNING;
					}
					
					errorSource.addError(new DefaultErrorSource.DefaultError(
							errorSource, errorKind, fileName, line,
							line_offset, end_offset, e.message));
				}
			}
		} else if(m.kind == PolyMarkup.INKIND_PROPERTIES) {
			LocationResponse l = new LocationResponse(m);
			CompileRequest cr = compileInfos.getFromParseID(l.parseID);
			
			// FIXME: synchronisation issue between getting buffer path and selecting right area in it?
			if(jEdit.getActiveView().getBuffer().getPath() == cr.fileName) {
				jEdit.getActiveView().getTextArea().setSelection(new Selection.Range(l.start,l.end));
			}
		} else if(m.kind == PolyMarkup.INKIND_TYPE_INFO) {
			LocationResponse l = new LocationResponse(m);
			CompileRequest cr = compileInfos.getFromParseID(l.parseID);
			
			if(jEdit.getActiveView().getBuffer().getPath() == cr.fileName) {
				jEdit.getActiveView().getTextArea().setSelection(new Selection.Range(l.start,l.end));
				Buffer srcBuffer = jEdit.getActiveView().getBuffer();
				int line = srcBuffer.getLineOfOffset(l.start);
				int line_offset = l.start - srcBuffer.getLineStartOffset(line);
				int end_line = srcBuffer.getLineOfOffset(l.end);
				int end_offset = 0;
				if (end_line == line) {
					end_offset = l.end - srcBuffer.getLineStartOffset(end_line);
				}
				
				if(l.markup.hasNext()) {
					String type_string = l.markup.next().getContent();
					errorSource.addError(new DefaultErrorSource.DefaultError(
							errorSource, ErrorSource.WARNING, srcBuffer.getPath(), line,
							line_offset, end_offset, "`" + type_string + "` is the type of: `" 
							+ l.getSrcLocTextOfBuffer(srcBuffer) + "`"));
				} else {
					errorSource.addError(new DefaultErrorSource.DefaultError(
							errorSource, ErrorSource.WARNING, srcBuffer.getPath(), line,
							line_offset, end_offset, "Not a value, so no type: `" 
							+ l.getSrcLocTextOfBuffer(srcBuffer) + "`"));
				}
			}
		} 
		// Location responses
		else if(m.kind == PolyMarkup.INKIND_LOC_DECLARED) {
			FullLocationResponse l = new FullLocationResponse(m);
			noteLocation(l);
		} else if(m.kind == PolyMarkup.INKIND_LOC_OF_PARENT_STRUCT) {
			FullLocationResponse l = new FullLocationResponse(m);
			noteLocation(l);
		} else if(m.kind == PolyMarkup.INKIND_LOC_WHERE_OPENED) {
			FullLocationResponse l = new FullLocationResponse(m);
			noteLocation(l);
		} 
		// movement responses (all the same)
		else if(m.kind == PolyMarkup.INKIND_MOVE_TO_FIRST_CHILD 
				|| m.kind == PolyMarkup.INKIND_MOVE_TO_NEXT
				|| m.kind == PolyMarkup.INKIND_MOVE_TO_PARENT
				|| m.kind == PolyMarkup.INKIND_MOVE_TO_PREVIOUS) {
			LocationResponse l = new LocationResponse(m);
			CompileRequest cr = compileInfos.getFromParseID(l.parseID);
			if(jEdit.getActiveView().getBuffer().getPath() == cr.fileName) {
				jEdit.getActiveView().getTextArea().setSelection(new Selection.Range(l.start,l.end));
			}
		}
	}

	public void add(PolyMarkup c, boolean isMore) { add(c); }

	public void close() { 
		// nothing special to do 
	}
		
}

