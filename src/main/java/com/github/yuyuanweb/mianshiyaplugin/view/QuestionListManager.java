package com.github.yuyuanweb.mianshiyaplugin.view;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.yuyuanweb.mianshiyaplugin.constant.PageConstant;
import com.github.yuyuanweb.mianshiyaplugin.constant.TextConstant;
import com.github.yuyuanweb.mianshiyaplugin.model.common.BaseResponse;
import com.github.yuyuanweb.mianshiyaplugin.model.common.Page;
import com.github.yuyuanweb.mianshiyaplugin.model.dto.QuestionBankQueryRequest;
import com.github.yuyuanweb.mianshiyaplugin.model.dto.QuestionQueryRequest;
import com.github.yuyuanweb.mianshiyaplugin.model.dto.TagCategoryQueryRequest;
import com.github.yuyuanweb.mianshiyaplugin.model.enums.DifficultyEnum;
import com.github.yuyuanweb.mianshiyaplugin.model.enums.NeedVipEnum;
import com.github.yuyuanweb.mianshiyaplugin.model.enums.QuestionDifficultyEnum;
import com.github.yuyuanweb.mianshiyaplugin.model.response.Question;
import com.github.yuyuanweb.mianshiyaplugin.model.response.QuestionBank;
import com.github.yuyuanweb.mianshiyaplugin.reader.SidebarController;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.NavigationContext;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.QuestionSelection;
import com.github.yuyuanweb.mianshiyaplugin.utils.PanelUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.*;
import com.intellij.ui.table.JBTable;
import com.intellij.openapi.project.Project;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.github.yuyuanweb.mianshiyaplugin.config.ApiConfig.readerMianShiYaApi;
import static com.github.yuyuanweb.mianshiyaplugin.constant.SearchComboBoxConstant.*;

/**
 * @author pine
 */
public class QuestionListManager {

    private JBPanel<?> paginationPanel;
    private MTabModel tableModel;

    private final int[] currentPage = new int[]{1};
    // 用于保存上次选中的值
    int initSelectedIndex = -2;
    final int[] comboBoxLastSelectedItem = {initSelectedIndex, initSelectedIndex, initSelectedIndex, initSelectedIndex};
    int nullIndex = -1;
    int questionBankLastSelectedIndex = 0;
    int tagLastSelectedIndex = 1;
    int difficultyLastSelectedIndex = 2;
    int needVipLastSelectedIndex = 3;

    private long searchGeneration;
    private boolean disposed;
    private JPanel panel;
    private Component listContent;
    private JBTable listTable;
    private JBScrollPane tableScrollPane;
    private Project project;
    private Consumer<QuestionSelection> onQuestionOpen;
    private Long loadedNavigationBankId;

    private final List<ComboBoxItem> difficultyComboBoxItems = Arrays.stream(QuestionDifficultyEnum.values())
            .map(item -> new ComboBoxItem(String.valueOf(item.getValue()), item.getText()))
            .collect(Collectors.toList());
    private final List<ComboBoxItem> needVipComboBoxItems = Arrays.stream(NeedVipEnum.values())
            .map(item -> new ComboBoxItem(String.valueOf(item.getValue()), item.getText()))
            .collect(Collectors.toList());
    private final QuestionQueryRequest questionQueryRequest;

    public QuestionListManager() {
        questionQueryRequest = new QuestionQueryRequest();
    }

    public void addQuestionTab(Long questionBankId, Project project) {
        SidebarController.getInstance(project).showQuestions(questionBankId);
    }

    /** Creates the persistent, project-scoped question list component. */
    public JComponent createPanel(Long bankId, Project project, Consumer<QuestionSelection> onQuestionOpen) {
        this.project = project;
        this.onQuestionOpen = onQuestionOpen;
        this.questionQueryRequest.setQuestionBankId(bankId);
        this.questionQueryRequest.setCurrent(PageConstant.FIRST_PAGE);
        this.currentPage[0] = PageConstant.FIRST_PAGE;
        this.panel = new JBPanel<>(new BorderLayout());
        panel.add(getSearchPanel(), BorderLayout.NORTH);
        paginationPanel = new JBPanel<>(new FlowLayout(FlowLayout.CENTER, 4, 2));
        panel.add(paginationPanel, BorderLayout.SOUTH);
        loadDataPanel();
        return panel;
    }

    /** Invalidates callbacks when this retained list is discarded or its project closes. */
    public void dispose() {
        disposed = true;
        searchGeneration++;
        panel = null;
        tableModel = null;
    }

    /**
     * 搜索条
     */
    private @NotNull JPanel getSearchPanel() {
        JBPanel<?> searchPanel = new JBPanel<>(new BorderLayout());

        SearchTextField searchField = new SearchTextField() {
            // 点击清除按钮后触发
            @Override
            protected void onFieldCleared() {
                questionQueryRequest.setCurrent(PageConstant.FIRST_PAGE);
                currentPage[0] = PageConstant.FIRST_PAGE;
                QuestionListManager.this.searchAndLoadData(questionQueryRequest);
            }
        };
        // 添加 DocumentListener 以监听内容变化
        searchField.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onTextChanged();
            }

            private void onTextChanged() {
                // 这里是文本变化时执行的操作
                String text = searchField.getText();
                questionQueryRequest.setTitle(text);
            }
        });
        // 绑定回车键事件
        searchField.getTextEditor().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // 回车键按下时执行的操作
                    String text = searchField.getText();
                    questionQueryRequest.setTitle(text);
                    questionQueryRequest.setCurrent(PageConstant.FIRST_PAGE);
                    currentPage[0] = PageConstant.FIRST_PAGE;
                    QuestionListManager.this.searchAndLoadData(questionQueryRequest);
                }
            }
        });

        searchField.getTextEditor().getEmptyText().setText(TextConstant.TITLE_SEARCH_PLACE_HOLDER);
        JButton searchButton = new JButton(TextConstant.SEARCH);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        // 绑定搜索按钮点击事件
        searchButton.addActionListener(e -> {
            String keyword = searchField.getText();
            questionQueryRequest.setTitle(keyword);
            questionQueryRequest.setCurrent(PageConstant.FIRST_PAGE);
            currentPage[0] = PageConstant.FIRST_PAGE;
            this.searchAndLoadData(questionQueryRequest);
        });
        ApplicationManager.getApplication().invokeLater(() -> {
            JPanel filterPanel = this.getFilterPanel();
            searchPanel.add(filterPanel, BorderLayout.SOUTH);
        });
        return searchPanel;
    }

    /**
     * 获取筛选框
     */
    private JPanel getFilterPanel() {
        JPanel filterPanel = new JPanel(new GridLayout(1, 0));
        // 初始化下拉列表
        // 题库 筛选框
        JComboBox<ComboBoxItem> questionBankComboBox = createCustomFilterBox(() -> {
            QuestionBankQueryRequest queryRequest = new QuestionBankQueryRequest();
            queryRequest.setCurrent(1);
            queryRequest.setPageSize(1000);
            try {
                BaseResponse<Page<QuestionBank>> baseResponse = readerMianShiYaApi.listQuestionBankVoByPage(queryRequest).execute().body();
                if (baseResponse == null || baseResponse.getData() == null || baseResponse.getData().getRecords() == null) return Collections.emptyList();
                return baseResponse.getData().getRecords().stream()
                        .map(questionBank -> new ComboBoxItem(questionBank.getId().toString(), questionBank.getTitle()))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, questionBankLastSelectedIndex, QUESTION_BANK_ID_FILE_NAME, QUESTION_BANK_PLACEHOLDER);
        filterPanel.add(questionBankComboBox);

        // 标签 筛选框
        JComboBox<ComboBoxItem> tagComboBox = createCustomFilterBox(() -> {
            TagCategoryQueryRequest queryRequest = new TagCategoryQueryRequest();
            queryRequest.setCurrent(1);
            try {
                BaseResponse<List<com.github.yuyuanweb.mianshiyaplugin.model.response.TagGroup>> response = readerMianShiYaApi.listTagCategory(queryRequest).execute().body();
                if (response == null || response.getData() == null) return Collections.emptyList();
                return response.getData().stream()
                        .flatMap(tagGroup -> tagGroup.getTagList().stream().map(tag -> new ComboBoxItem(tag, tag)).collect(Collectors.toList()).stream())
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, tagLastSelectedIndex, TAG_LIST_FILE_NAME, TAG_LIST_PLACEHOLDER);
        filterPanel.add(tagComboBox);

        // 难度 筛选框
        JComboBox<ComboBoxItem> difficultyComboBox = createCustomFilterBox(() -> difficultyComboBoxItems, difficultyLastSelectedIndex, DIFFICULTY_FILE_NAME, DIFFICULTY_PLACEHOLDER);
        filterPanel.add(difficultyComboBox);

        // 是否需要会员 筛选框
        JComboBox<ComboBoxItem> needVipComboBox = createCustomFilterBox(() -> needVipComboBoxItems, needVipLastSelectedIndex, NEED_VIP_FILE_NAME, NEED_VIP_PLACEHOLDER);
        filterPanel.add(needVipComboBox);

        return filterPanel;
    }


    // 创建带有自定义渲染器和点击处理的筛选框
    private JComboBox<ComboBoxItem> createCustomFilterBox(Supplier<List<ComboBoxItem>> supplier, int lastSelectedIndex, String fieldName, String placeHolder) {
        ComboBox<ComboBoxItem> comboBox = new ComboBox<>();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<ComboBoxItem> loadedItems;
            try { loadedItems = supplier.get(); } catch (RuntimeException ex) { loadedItems = Collections.emptyList(); }
            final List<ComboBoxItem> comboBoxItems = loadedItems;
            if (disposed || project == null || project.isDisposed()) return;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed || project == null || project.isDisposed()) return;
                comboBox.setModel(new DefaultComboBoxModel<>(ArrayUtil.toArray(comboBoxItems, ComboBoxItem.class)));
                Long questionBankId = questionQueryRequest.getQuestionBankId();
                if (QUESTION_BANK_ID_FILE_NAME.equals(fieldName) && comboBoxLastSelectedItem[questionBankLastSelectedIndex] == initSelectedIndex && questionBankId != null) {
                    int index = comboBoxItems.indexOf(new ComboBoxItem(questionBankId.toString(), null));
                    comboBox.setSelectedIndex(index);
                    comboBox.repaint();
                    comboBoxLastSelectedItem[lastSelectedIndex] = index;
                } else {
                    comboBox.setSelectedIndex(nullIndex);
                    comboBoxLastSelectedItem[lastSelectedIndex] = nullIndex;
                }
                comboBox.setRenderer(new CheckmarkRenderer(comboBox, placeHolder));
                comboBox.addActionListener(new ActionListener() {
                    // 防止递归调用
                    private boolean ignoreAction = false;

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (ignoreAction) {
                            return;
                        }
                        ignoreAction = true;

                        int selectedIndex = comboBox.getSelectedIndex();
                        // 获取下拉列表选中的值
                        ComboBoxItem selectedItem = (ComboBoxItem) comboBox.getSelectedItem();
                        if (comboBoxLastSelectedItem[lastSelectedIndex] == comboBox.getSelectedIndex()) {
                            ReflectUtil.setFieldValue(questionQueryRequest, fieldName, null);
                        } else {
                            assert selectedItem != null;
                            String selectedItemKey = selectedItem.key;
                            if (TAG_LIST_FILE_NAME.equals(fieldName)) {
                                ReflectUtil.setFieldValue(questionQueryRequest, fieldName, Arrays.asList(selectedItemKey.getClass().cast(selectedItemKey)));
                            } else {
                                ReflectUtil.setFieldValue(questionQueryRequest, fieldName, selectedItemKey.getClass().cast(selectedItemKey));
                            }
                        }

                        if (comboBox.getSelectedItem() != null) {
                            if (comboBoxLastSelectedItem[lastSelectedIndex] == selectedIndex) {
                                // 如果已经有对号，再次点击时取消选中并恢复为默认状态
                                comboBox.setSelectedIndex(nullIndex);
                                selectedIndex = nullIndex;
                            }
                        }
                        comboBoxLastSelectedItem[lastSelectedIndex] = selectedIndex;
                        ignoreAction = false;

                        questionQueryRequest.setCurrent(PageConstant.FIRST_PAGE);
                        currentPage[0] = PageConstant.FIRST_PAGE;
                        QuestionListManager.this.searchAndLoadData(questionQueryRequest);
                    }
                });
            });
        });
        return comboBox;
    }

    // 自定义渲染器，处理对号的显示
    private static class CheckmarkRenderer extends DefaultListCellRenderer {
        private final JComboBox<ComboBoxItem> comboBox;
        private final String placeHolder;

        public CheckmarkRenderer(JComboBox<ComboBoxItem> comboBox, String placeHolder) {
            this.comboBox = comboBox;
            this.placeHolder = placeHolder;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (index == comboBox.getSelectedIndex()) {
                label.setText("✓ " + value);
            } else {
                label.setText(value != null ? value.toString() : "");
            }
            if (value == null) {
                label.setText(placeHolder);
            }
            return label;
        }
    }

    // 自定义类，用于存储 key-value
    @AllArgsConstructor
    public static class ComboBoxItem {
        private String key;
        private String value;

        @Override
        public String toString() {
            // 显示在 JComboBox 中的内容
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ComboBoxItem) {
                ComboBoxItem item = (ComboBoxItem) o;
                return key.equals(item.key);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }


    /**
     * 数据表格
     */
    private void loadDataPanel() {
        final long generation = ++searchGeneration;
        final QuestionQueryRequest request = copyQuery(questionQueryRequest);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            BaseResponse<Page<Question>> data = null;
            RuntimeException failure = null;
            try { data = this.fetchQuestionList(request); }
            catch (RuntimeException ex) { failure = ex; }
            final BaseResponse<Page<Question>> result = data;
            final RuntimeException error = failure;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!isCurrent(generation)) return;
                if (error != null || result == null || result.getData() == null || result.getData().getRecords() == null) {
                    showInlineError(error == null ? "题目列表暂不可用" : "题目列表加载失败，请重试");
                    return;
                }
                loadedNavigationBankId = navigationBankId(request);
                // 创建表格数据模型
                tableModel = new MTabModel();
                tableModel.addColumn("id");
                tableModel.addColumn("标题");
                tableModel.addColumn("难度");
                tableModel.addColumn("标签");
                tableModel.addColumn("number");

                // 将数据添加到表格模型
                for (Question row : result.getData().getRecords()) {
                    DifficultyEnum difficultyEnum = DifficultyEnum.getEnumByValue(row.getDifficulty());
                    String difficulty = null;
                    if (difficultyEnum != null) {
                        difficulty = difficultyEnum.getText();
                    }
                    tableModel.addRow(new Object[]{row.getId().toString(), row.getTitle(), difficulty, row.getTagList(), row.getQuestionNum()});
                }

                // 创建表格
                JBTable table = PanelUtil.createTablePanel(tableModel, (tempTable, mouseEvent) -> {
                    int selectedRow = tempTable.getSelectedRow();
                    if (selectedRow < 0 || onQuestionOpen == null || disposed || project == null || project.isDisposed()) return;
                    Long questionId = Long.valueOf((String) tempTable.getValueAt(selectedRow, 0));
                    String questionTitle = Objects.toString(tempTable.getValueAt(selectedRow, 1), "");
                    List<Long> ids = new ArrayList<>();
                    for (int row = 0; row < tempTable.getRowCount(); row++) ids.add(Long.valueOf((String) tempTable.getValueAt(row, 0)));
                    Long bankId = loadedNavigationBankId;
                    onQuestionOpen.accept(new QuestionSelection(questionId, questionTitle, new NavigationContext(bankId, ids, questionId)));
                }, 3);

                // 设置列宽为0，使列存在但不可见
                // 获取表格列模型
                TableColumnModel columnModel = table.getColumnModel();
                TableColumn column = columnModel.getColumn(4);
                column.setMinWidth(0);
                column.setMaxWidth(0);
                column.setPreferredWidth(0);
                table.setFillsViewportHeight(true);

                // 添加监听器，监听 table 大小变化
                table.addComponentListener(new ComponentAdapter() {
                    @Override public void componentResized(ComponentEvent e) {
                        int width = Math.max(0, table.getParent() == null ? table.getWidth() : table.getParent().getWidth());
                        if (width < 380) {
                            columnModel.getColumn(1).setPreferredWidth(Math.max(150, width - 24));
                            columnModel.getColumn(2).setMinWidth(0); columnModel.getColumn(2).setMaxWidth(0); columnModel.getColumn(2).setPreferredWidth(0);
                            columnModel.getColumn(3).setMinWidth(0); columnModel.getColumn(3).setMaxWidth(0); columnModel.getColumn(3).setPreferredWidth(0);
                        } else {
                            columnModel.getColumn(2).setMinWidth(45); columnModel.getColumn(2).setMaxWidth(Integer.MAX_VALUE);
                            columnModel.getColumn(3).setMinWidth(70); columnModel.getColumn(3).setMaxWidth(Integer.MAX_VALUE);
                            columnModel.getColumn(1).setPreferredWidth((int) (width * .58));
                            columnModel.getColumn(2).setPreferredWidth((int) (width * .14));
                            columnModel.getColumn(3).setPreferredWidth((int) (width * .28));
                        }
                        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                        table.revalidate();
                    }
                });

                // 将表格添加到滚动面板
                JBScrollPane scrollPane = new JBScrollPane(table);
                listTable = table;
                tableScrollPane = scrollPane;
                // 确保表格充满视口
                scrollPane.setViewportView(table);
                if (listContent != null) panel.remove(listContent);
                listContent = scrollPane;
                panel.add(scrollPane, BorderLayout.CENTER);

                // 更新分页条
                PanelUtil.updatePaginationPanel(paginationPanel, result.getData().getTotal(), currentPage, this::loadPage);
                panel.revalidate(); panel.repaint();
            });
        });
    }

    private boolean isCurrent(long generation) {
        return !disposed && generation == searchGeneration && project != null && !project.isDisposed() && panel != null;
    }

    private void showInlineError(String message) {
        if (listContent != null) panel.remove(listContent);
        JBPanel<?> errorPanel = new JBPanel<>(new FlowLayout(FlowLayout.CENTER));
        errorPanel.add(new JBLabel(message));
        JButton retry = new JButton("重试");
        retry.addActionListener(e -> loadDataPanel());
        errorPanel.add(retry);
        JButton login = new JButton(TextConstant.LOGIN);
        login.addActionListener(e -> new LoginPanel(project).show());
        errorPanel.add(login);
        listContent = errorPanel;
        panel.add(errorPanel, BorderLayout.CENTER);
        panel.revalidate(); panel.repaint();
    }

    public BaseResponse<Page<Question>> fetchQuestionList(QuestionQueryRequest queryRequest) {
        // Set up request body
        if (queryRequest == null) {
            queryRequest = questionQueryRequest;
        }

        try {
            if (StrUtil.isNotBlank(queryRequest.getTitle())) {
                // 如果 title 不为空，使用搜索逻辑
                return readerMianShiYaApi.searchQuestionList(queryRequest).execute().body();
            } else if (ObjUtil.isNotEmpty(queryRequest.getQuestionBankId())) {
                // 如果 questionBankId 不为空，从题库中查找
                return readerMianShiYaApi.listQuestionByQuestionBank(queryRequest).execute().body();
            } else {
                return readerMianShiYaApi.getQuestionList(queryRequest).execute().body();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadPage(int page, int pageSize) {
        // 实现分页逻辑，并更新表格数据
        questionQueryRequest.setPageSize(pageSize);
        questionQueryRequest.setCurrent(page);
        this.searchAndLoadData(questionQueryRequest);
    }

    private void searchAndLoadData(QuestionQueryRequest queryRequest) {
        // 清空现有表格数据
        if (tableModel == null) {
            return;
        }
        final long generation = ++searchGeneration;
        final QuestionQueryRequest request = copyQuery(queryRequest);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            BaseResponse<Page<Question>> response = null;
            RuntimeException failure = null;
            try { response = this.fetchQuestionList(request); }
            catch (RuntimeException ex) { failure = ex; }
            final BaseResponse<Page<Question>> result = response;
            final RuntimeException error = failure;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!isCurrent(generation)) return;
                if (error != null || result == null || result.getData() == null || result.getData().getRecords() == null) {
                    showInlineError("题目列表加载失败，请重试");
                    return;
                }
                loadedNavigationBankId = navigationBankId(request);
                tableModel.setRowCount(0);
                Page<Question> data = result.getData();

                // 添加新数据到表格模型
                for (Question row : data.getRecords()) {
                    DifficultyEnum difficultyEnum = DifficultyEnum.getEnumByValue(row.getDifficulty());
                    String difficulty = null;
                    if (difficultyEnum != null) {
                        difficulty = difficultyEnum.getText();
                    }
                    tableModel.addRow(new Object[]{row.getId().toString(), row.getTitle(), difficulty, row.getTagList(), row.getQuestionNum()});
                }

                // 重新渲染表格
                tableModel.fireTableDataChanged();
                if (listContent != tableScrollPane && tableScrollPane != null) {
                    if (listContent != null) panel.remove(listContent);
                    listContent = tableScrollPane;
                    panel.add(tableScrollPane, BorderLayout.CENTER);
                }
                PanelUtil.updatePaginationPanel(paginationPanel, data.getTotal(), currentPage, this::loadPage);
                if (listTable != null) listTable.revalidate();
                panel.revalidate(); panel.repaint();
            });
        });
    }

    /** Only an unfiltered bank browser may expand navigation to the entire bank. */
    public static Long navigationBankId(QuestionQueryRequest request) {
        if (StrUtil.isNotBlank(request.getTitle()) || (request.getTagList() != null && !request.getTagList().isEmpty())
                || request.getDifficulty() != null || request.getNeedVip() != null) return null;
        return request.getQuestionBankId();
    }

    private static QuestionQueryRequest copyQuery(QuestionQueryRequest source) {
        QuestionQueryRequest copy = new QuestionQueryRequest();
        copy.setTitle(source.getTitle());
        copy.setTagList(source.getTagList() == null ? null : new ArrayList<>(source.getTagList()));
        copy.setQuestionBankId(source.getQuestionBankId());
        copy.setNeedVip(source.getNeedVip());
        copy.setDifficulty(source.getDifficulty());
        copy.setCurrent(source.getCurrent());
        copy.setPageSize(source.getPageSize());
        return copy;
    }
}
