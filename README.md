<img width="1448" height="798" alt="image" src="https://github.com/user-attachments/assets/264a1804-4def-45d1-94fa-99a913749f7a" />

Common problem: Empty Tool Window

If the Call Graph tool window is empty and the generated diagram is not displayed, JCEF may be running in out-of-process mode.

Solution:

Open Help → Edit Custom VM Options...
Add the following line: ''' -Dide.browser.jcef.out-of-process.enabled=false '''
Restart IntelliJ IDEA.
After restarting, the Call Graph window should display the generated Mermaid diagram correctly.
