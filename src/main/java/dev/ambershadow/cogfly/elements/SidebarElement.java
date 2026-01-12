package dev.ambershadow.cogfly.elements;

import com.formdev.flatlaf.FlatLaf;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.util.FrameManager;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;

public class SidebarElement extends JPanel {

    private static final int COLLAPSED_WIDTH = 40;
    private static final int EXPANDED_WIDTH = 200;
    private static final int ANIMATION_STEP = 2;
    private static final int TIMER_DELAY = 2;
    private float textProgress = 0f;
    public SidebarElement() {
        super();
        setBackground(UIManager.getColor("Panel.background").darker());
        setPreferredSize(new Dimension(COLLAPSED_WIDTH, FrameManager.getOrCreate().frame.getHeight()));

        FadeButton savesButton = new FadeButton();
        savesButton.setText("Open Saves Folder");
        savesButton.setIcon(Assets.openSaves.getAsIconWithColor(Color.WHITE));
        savesButton.setHorizontalAlignment(SwingConstants.LEFT);
        savesButton.setTextAlpha(0f);
        savesButton.setPreferredSize(new Dimension(EXPANDED_WIDTH, savesButton.getPreferredSize().height));
        savesButton.setMaximumSize(new Dimension(EXPANDED_WIDTH, savesButton.getMaximumSize().height));
        savesButton.setMinimumSize(new Dimension(EXPANDED_WIDTH, savesButton.getMinimumSize().height));
        savesButton.addActionListener(_ -> Utils.openSavePath());

        FadeButton logsButton = new FadeButton();
        logsButton.setText("Open Logs Folder");
        logsButton.setIcon(Assets.openSaves.getAsIconWithColor(Color.WHITE));
        logsButton.setHorizontalAlignment(SwingConstants.LEFT);
        logsButton.setTextAlpha(0f);
        logsButton.setPreferredSize(new Dimension(EXPANDED_WIDTH, logsButton.getPreferredSize().height));
        logsButton.setMaximumSize(new Dimension(EXPANDED_WIDTH, logsButton.getMaximumSize().height));
        logsButton.setMinimumSize(new Dimension(EXPANDED_WIDTH, logsButton.getMinimumSize().height));
        logsButton.addActionListener(_ -> Utils.openPath(Paths.get(Cogfly.localDataPath).resolve("logs")));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setOpaque(false);
        buttonPanel.add(savesButton);
        buttonPanel.add(logsButton);

        setLayout(new BorderLayout());
        add(buttonPanel, BorderLayout.SOUTH);

        Timer expandTimer = new Timer(TIMER_DELAY, null);
        Timer collapseTimer = new Timer(TIMER_DELAY, null);

        expandTimer.addActionListener(_ -> {
            int width = getWidth();
            setBackground(UIManager.getColor("Panel.background").darker());
            savesButton.setIcon(Assets.openSaves.getAsIconWithColor(
                    FlatLaf.isLafDark() ? Color.WHITE : Color.BLACK
            ));
            if (width < EXPANDED_WIDTH) {
                setPreferredSize(new Dimension(
                        Math.min(width + ANIMATION_STEP, EXPANDED_WIDTH),
                        FrameManager.getOrCreate().frame.getHeight()
                ));

                textProgress = (float)(getWidth() - COLLAPSED_WIDTH) /
                        (EXPANDED_WIDTH - COLLAPSED_WIDTH);
                textProgress = Math.max(0f, Math.min(1f, textProgress));
                savesButton.setTextAlpha(textProgress);
                logsButton.setTextAlpha(textProgress);

                revalidate();
                repaint();
            } else {
                expandTimer.stop();
            }
        });

        collapseTimer.addActionListener(_ -> {
            int width = getWidth();
            setBackground(UIManager.getColor("Panel.background").darker());
            savesButton.setIcon(Assets.openSaves.getAsIconWithColor(
                    FlatLaf.isLafDark() ? Color.WHITE : Color.BLACK
            ));
            if (width > COLLAPSED_WIDTH) {
                setPreferredSize(new Dimension(
                        Math.max(width - ANIMATION_STEP, COLLAPSED_WIDTH),
                        FrameManager.getOrCreate().frame.getHeight()
                ));

                textProgress = ((float)(getWidth() - COLLAPSED_WIDTH) /
                        (EXPANDED_WIDTH - COLLAPSED_WIDTH)) * -1;
                textProgress = Math.max(0f, Math.min(1f, textProgress));
                savesButton.setTextAlpha(textProgress);
                logsButton.setTextAlpha(textProgress);
                revalidate();
                repaint();
            } else {
                collapseTimer.stop();
            }
        });

        Timer hoverCheck = new Timer(50, _ -> {
            Point p = MouseInfo.getPointerInfo().getLocation();
            setBackground(UIManager.getColor("Panel.background").darker());
            SwingUtilities.convertPointFromScreen(p, this);
            if (contains(p)) {
                collapseTimer.stop();
                expandTimer.start();
            } else {
                expandTimer.stop();
                collapseTimer.start();
            }
        });
        hoverCheck.start();
    }

    static class FadeButton extends JButton {
        private float textAlpha = 0f;

        public void setTextAlpha(float alpha) {
            textAlpha = Math.max(0f, Math.min(1f, alpha));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            String text = getText();
            setText("");
            super.paintComponent(g);
            setText(text);

            if (text == null || text.isEmpty() || textAlpha <= 0f)
                return;

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setClip(0, 0, getWidth(), getHeight());

            g2.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, textAlpha
            ));

            g2.setFont(getFont());
            g2.setColor(getForeground());

            FontMetrics fm = g2.getFontMetrics();
            int x = (getIcon() != null)
                    ? getIcon().getIconWidth() + getIconTextGap() + 4
                    : 4;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

            g2.drawString(text, x, y);
            g2.dispose();
        }
    }
}
