package dev.ambershadow.cogfly.util;

import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.elements.InfoPageElement;
import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.elements.profiles.ProfileCardElement;
import dev.ambershadow.cogfly.elements.profiles.ProfilesScreenElement;
import dev.ambershadow.cogfly.elements.SelectedPageButtonElement;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.function.Consumer;

public class FrameManager {

    public static FrameManager getOrCreate(){
        isCreated = true;
        return instance != null ? instance : new FrameManager();
    }
    public static boolean isCreated;
    private static FrameManager instance;
    public final JFrame frame;
    public final Dimension screenSize;
    public SelectedPageButtonElement profilesPageButton;
    private final JPanel pagePanel;
    public JPanel getPagePanel() {
        return pagePanel;
    }
    private FrameManager(){
        instance = this;
        pagePanel = new JPanel(new CardLayout());
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame = new JFrame();
        frame.setTitle("Cogfly");
        frame.setResizable(false);
        frame.setMinimumSize(new Dimension(1200, 750));
        frame.setPreferredSize(new Dimension(1200, 750));
        frame.setLocation(screenSize.width/2-frame.getWidth()/2,screenSize.height/2-frame.getHeight()/2);
        frame.setIconImage(Assets.icon.getAsImage());
        frame.setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());

        JPanel topPanel = new JPanel(new GridLayout(1, CogflyPage.values().length, 25, 0));

        int sidePadding = 20;
        topPanel.add(Box.createHorizontalStrut(sidePadding));
        for (CogflyPage value : CogflyPage.values()) {
            pagePanel.add(value.page, value.pageString);
            SelectedPageButtonElement button = new SelectedPageButtonElement(value.pageString);
            button.addActionListener(_ -> setPage(value, button));
            topPanel.add(button);
            if (value == CogflyPage.INFO)
                setPage(CogflyPage.INFO, button);
            if (value == CogflyPage.PROFILES)
                profilesPageButton = button;
        }
        Border padding = BorderFactory.createEmptyBorder(0, 0, 5, 0);
        Border color =
                new MatteBorder(0, 0, 5, 0, UIManager.getColor("Panel.background").darker()){
                    @Override
                    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height){
                        color = UIManager.getColor("Panel.background").darker();
                        super.paintBorder(c, g, x, y, width, height);
                    }
                };
        topPanel.setBorder(BorderFactory.createCompoundBorder(color, padding));
        topPanel.add(Box.createHorizontalStrut(sidePadding));
        frame.add(pagePanel, BorderLayout.CENTER);
        frame.add(topPanel, BorderLayout.NORTH);
    }

    private SelectedPageButtonElement currentPageButton = null;

    private CogflyPage currentPage = null;
    public CogflyPage getCurrentPage(){
        return currentPage;
    }
    public SelectedPageButtonElement getCurrentPageButton() {
        return currentPageButton;
    }
    public void setPage(CogflyPage page, SelectedPageButtonElement button){
        // Button.selectedForeground
        // Button.default.hoverBorderColor
        // Button.default.focusColor
        // Button.selectedBackground
        // Button.highlight
        // Button.borderColor
        // Button.focus
        // Button.hoverBorderColor
        // Button.select
        if (page.equals(CogflyPage.SETTINGS)){
            JDialog dialog = new SettingsDialog(frame, "Settings", true);
            dialog.setVisible(true);
        } else {
            Color unhoveredColor = UIManager.getColor("Button.background");
            CardLayout layout = (CardLayout) pagePanel.getLayout();
            layout.show(pagePanel, page.pageString);
            currentPage = page;
            page.reload();
            if (currentPageButton != null) {
                currentPageButton.setBackground(unhoveredColor);
                currentPageButton.selected = false;
            }
            currentPageButton = button;
            currentPageButton.selected = true;
            currentPageButton.setBackground(ProfileCardElement.hover);
        }
    }
    public enum CogflyPage {
        INFO("Info", new InfoPageElement()),
        PROFILES("Profiles", new ProfilesScreenElement()),
        SETTINGS("Settings", (_) -> {})
        ;

        private final String pageString;
        private final JPanel page;
        CogflyPage(String string, JPanel page){
            this.pageString = string;
            this.page = page;
            page.setName(string);
        }

        CogflyPage(String string, Consumer<JPanel> action){
            this.pageString = string;
            this.page = new JPanel();
            page.setName(string);
            action.accept(page);
        }

        public void reload(){
            if (page instanceof ReloadablePage reload)
                reload.reload();
        }
    }
}