<img width="1215" height="753" alt="image" src="https://github.com/user-attachments/assets/e736e228-b9be-4070-9ff0-d7f6346efec9" />

Common problem: Empty Tool Window

If the Call Graph tool window is empty and the generated diagram is not displayed, JCEF may be running in out-of-process mode.

Solution:

Open Help → Edit Custom VM Options...
Add the following line: ''' -Dide.browser.jcef.out-of-process.enabled=false '''
Restart IntelliJ IDEA.
After restarting, the Call Graph window should display the generated Mermaid diagram correctly.
