package dev.autostock.client;
public final class DraftScreen extends PreviewScreen {
 public DraftScreen(ClientDraft draft){super(draft);}
 void selectTab(int legacy){navigate(switch(legacy){case 0->1;case 1,2,3->2;case 4->3;case 5->4;default->0;});}
}
