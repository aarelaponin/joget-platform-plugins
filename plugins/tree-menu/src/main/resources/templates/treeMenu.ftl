<#-- Tree Menu Template - Supports Metamodel/Demo and Legacy modes -->
<#if mode?? && mode == "metamodel">
    <#-- ========== METAMODEL / DEMO MODE ========== -->
    <#-- Visible anchor menu item - required for UI Builder selection -->
    <li id="${treeId}_anchor" class="menu-item tm-anchor">
        <a href="javascript:void(0);" class="menu-link">
            <i class="fas fa-sitemap"></i>
            <span>${label!""}</span>
        </a>
    </li>

    <style>
        /* Tree nodes inherit theme styling - no forced colors */
        .tm-node,
        .tm-node.menu-item,
        li.tm-node {
            background: transparent !important;
        }
        .tm-node > .menu-link,
        .tm-node > a.menu-link,
        li.tm-node > .menu-link,
        li.tm-node > a.menu-link {
            background: transparent !important;
        }
        /* Let text color inherit from theme - don't force white */
        .tm-node > .menu-link,
        .tm-node > .menu-link span,
        .tm-node > .menu-link i {
            opacity: 1 !important;
        }
        .tm-node > .menu-link:hover,
        li.tm-node > .menu-link:hover {
            background: rgba(0,0,0,0.05) !important;
        }
        .tm-node.tm-hidden {
            display: none !important;
        }
        .tm-node .tm-arrow {
            margin-left: 6px;
            font-size: 10px;
            transition: transform 0.2s;
        }
        /* Loading and empty states slightly dimmed */
        .tm-node.tm-loading > .menu-link,
        .tm-node.tm-loading > .menu-link span,
        .tm-node.tm-empty > .menu-link,
        .tm-node.tm-empty > .menu-link span,
        .tm-node.tm-load-more > .menu-link,
        .tm-node.tm-load-more > .menu-link span {
            opacity: 0.6 !important;
        }
    </style>

    <script>
        jQuery(document).ready(function($) {
            var treeId = '${treeId}';
            var ajaxUrl = '${ajaxUrl!""}';
            var pageSize = ${pageSize!50};
            var initialData = ${initialTreeData!"[]"};

            var $anchor = $('#' + treeId + '_anchor');
            var $menuList = $anchor.parent();

            // Create a tree node matching sidebar menu structure
            function createNode(node, level) {
                level = level || 0;
                var hasChildren = node.children === true;

                var $li = $('<li class="menu-item tm-node"></li>');
                $li.attr('data-id', node.id);
                $li.attr('data-level', level);
                $li.attr('data-has-children', hasChildren);

                // Small indent: 5px per level, max 2 levels
                var indentPx = Math.min(level, 2) * 5;
                var $link = $('<a href="javascript:void(0);" class="menu-link" style="padding-left:' + (10 + indentPx) + 'px !important;"></a>');

                // Just the text label, no icon
                $link.append('<span>' + (node.text || '') + '</span>');

                if (hasChildren) {
                    $link.append('<i class="fas fa-angle-right tm-arrow"></i>');
                }

                $li.append($link);
                $li.data('node', node);
                $li.data('expanded', false);
                $li.data('loaded', !hasChildren);

                return $li;
            }

            // Find where to insert children (after the parent and its existing children)
            function findInsertPosition($parent) {
                var parentLevel = parseInt($parent.attr('data-level')) || 0;
                var $current = $parent;

                // Skip all items that are children of this parent (higher level)
                while ($current.next('.tm-node').length > 0) {
                    var $next = $current.next('.tm-node');
                    var nextLevel = parseInt($next.attr('data-level')) || 0;
                    if (nextLevel > parentLevel) {
                        $current = $next;
                    } else {
                        break;
                    }
                }
                return $current;
            }

            // Handle click
            $menuList.on('click', '.tm-node > .menu-link', function(e) {
                e.preventDefault();
                e.stopPropagation();

                var $link = $(this);
                var $li = $link.closest('.tm-node');
                var node = $li.data('node');
                var hasChildren = $li.attr('data-has-children') === 'true';
                var isExpanded = $li.data('expanded');
                var isLoaded = $li.data('loaded');
                var level = parseInt($li.attr('data-level')) || 0;

                if (hasChildren) {
                    if (isExpanded) {
                        // Collapse - hide all descendants
                        collapseNode($li);
                    } else {
                        // Expand
                        $li.data('expanded', true);
                        $link.find('.tm-arrow').css('transform', 'rotate(90deg)');

                        if (!isLoaded) {
                            loadChildren($li, node, level + 1);
                        } else {
                            // Show existing children
                            showChildren($li);
                        }
                    }
                }

                // Fire event for records
                if (node && node.type === 'record') {
                    $(document).trigger('treeMenuRecordSelected', {
                        recordId: node.data ? node.data.recordId : null,
                        formId: node.data ? node.data.formId : null,
                        configId: node.data ? node.data.configId : null
                    });
                }
            });

            function collapseNode($li) {
                $li.data('expanded', false);
                $li.find('> .menu-link .tm-arrow').css('transform', 'rotate(0deg)');

                var parentLevel = parseInt($li.attr('data-level')) || 0;
                var $current = $li.next('.tm-node');

                while ($current.length > 0) {
                    var currentLevel = parseInt($current.attr('data-level')) || 0;
                    if (currentLevel > parentLevel) {
                        $current.hide();
                        $current.data('expanded', false);
                        $current.find('> .menu-link .tm-arrow').css('transform', 'rotate(0deg)');
                        $current = $current.next('.tm-node');
                    } else {
                        break;
                    }
                }
            }

            function showChildren($li) {
                var parentLevel = parseInt($li.attr('data-level')) || 0;
                var childLevel = parentLevel + 1;
                var $current = $li.next('.tm-node');

                while ($current.length > 0) {
                    var currentLevel = parseInt($current.attr('data-level')) || 0;
                    if (currentLevel === childLevel) {
                        $current.show();
                        $current = $current.next('.tm-node');
                    } else if (currentLevel > childLevel) {
                        // Skip grandchildren (they stay hidden unless parent is expanded)
                        $current = $current.next('.tm-node');
                    } else {
                        break;
                    }
                }
            }

            function loadChildren($li, node, childLevel) {
                var configId = (node.data && node.data.configId) || (node.id || '').replace('config_', '');
                var parentRecordId = (node.type === 'record' && node.data) ? node.data.recordId : '';

                // Show loading indicator
                var $insertAfter = findInsertPosition($li);
                var $loading = $('<li class="menu-item tm-node tm-loading"></li>');
                $loading.attr('data-level', childLevel);
                var $loadLink = $('<a class="menu-link" style="padding-left:' + (15 + childLevel * 20) + 'px;opacity:0.6;"></a>');
                $loadLink.append('<i class="fas fa-spinner fa-spin"></i>');
                $loadLink.append('<span>Loading...</span>');
                $loading.append($loadLink);
                $insertAfter.after($loading);

                $.ajax({
                    url: ajaxUrl + (ajaxUrl.indexOf('?') > -1 ? '&' : '?') + '_ajaxTreeLoad=true',
                    data: { action: 'children', configId: configId, parentRecordId: parentRecordId, page: 1, pageSize: pageSize },
                    dataType: 'json',
                    success: function(r) {
                        $loading.remove();
                        $li.data('loaded', true);

                        if (!r.nodes || r.nodes.length === 0) {
                            var $empty = $('<li class="menu-item tm-node tm-empty"></li>');
                            $empty.attr('data-level', childLevel);
                            $empty.attr('data-parent-id', $li.attr('data-id'));
                            var $emptyLink = $('<a class="menu-link" style="padding-left:' + (15 + childLevel * 20) + 'px;opacity:0.5;font-style:italic;"></a>');
                            $emptyLink.append('<span>No items</span>');
                            $empty.append($emptyLink);
                            findInsertPosition($li).after($empty);
                            return;
                        }

                        var $insertPos = findInsertPosition($li);
                        r.nodes.forEach(function(n) {
                            var $child = createNode(n, childLevel);
                            $child.attr('data-parent-id', $li.attr('data-id'));
                            $insertPos.after($child);
                            $insertPos = $child;
                        });

                        if (r.hasMore) {
                            var $more = $('<li class="menu-item tm-node tm-load-more"></li>');
                            $more.attr('data-level', childLevel);
                            $more.attr('data-parent-id', $li.attr('data-id'));
                            $more.data({ page: 2, configId: configId, parentRecordId: parentRecordId, childLevel: childLevel, parentLi: $li });
                            var $moreLink = $('<a href="javascript:void(0);" class="menu-link" style="padding-left:' + (15 + childLevel * 20) + 'px;opacity:0.6;"></a>');
                            $moreLink.append('<span>Load more...</span>');
                            $more.append($moreLink);
                            $insertPos.after($more);
                        }
                    },
                    error: function() {
                        $loading.find('span').text('Error loading');
                    }
                });
            }

            // Load more handler
            $menuList.on('click', '.tm-load-more > .menu-link', function(e) {
                e.stopPropagation();
                var $more = $(this).closest('.tm-load-more');
                var pg = $more.data('page');
                var cid = $more.data('configId');
                var pid = $more.data('parentRecordId');
                var childLevel = $more.data('childLevel');
                var $parentLi = $more.data('parentLi');

                $(this).find('span').text('Loading...');

                $.ajax({
                    url: ajaxUrl + (ajaxUrl.indexOf('?') > -1 ? '&' : '?') + '_ajaxTreeLoad=true',
                    data: { action: 'children', configId: cid, parentRecordId: pid, page: pg, pageSize: pageSize },
                    dataType: 'json',
                    success: function(r) {
                        var $insertPos = $more.prev('.tm-node');
                        $more.remove();

                        (r.nodes || []).forEach(function(n) {
                            var $child = createNode(n, childLevel);
                            $child.attr('data-parent-id', $parentLi.attr('data-id'));
                            $insertPos.after($child);
                            $insertPos = $child;
                        });

                        if (r.hasMore) {
                            var $newMore = $('<li class="menu-item tm-node tm-load-more"></li>');
                            $newMore.attr('data-level', childLevel);
                            $newMore.attr('data-parent-id', $parentLi.attr('data-id'));
                            $newMore.data({ page: pg + 1, configId: cid, parentRecordId: pid, childLevel: childLevel, parentLi: $parentLi });
                            var $moreLink = $('<a href="javascript:void(0);" class="menu-link" style="padding-left:' + (15 + childLevel * 20) + 'px;opacity:0.6;"></a>');
                            $moreLink.append('<span>Load more...</span>');
                            $newMore.append($moreLink);
                            $insertPos.after($newMore);
                        }
                    },
                    error: function() { $more.find('span').text('Retry'); }
                });
            });

            // Build initial tree - all nodes as flat siblings
            function buildTree() {
                if (initialData.length === 0) {
                    return;
                }

                // Sort by hierarchy for correct insertion order
                function getPath(node) {
                    var path = [];
                    var current = node;
                    while (current) {
                        path.unshift(current.id);
                        current = initialData.find(function(n) { return n.id === current.parent; });
                    }
                    return path.join('/');
                }

                var sorted = initialData.slice().sort(function(a, b) {
                    return getPath(a).localeCompare(getPath(b));
                });

                // Calculate levels
                function getLevel(node) {
                    var level = 0;
                    var current = node;
                    while (current.parent && current.parent !== '#') {
                        level++;
                        current = initialData.find(function(n) { return n.id === current.parent; });
                        if (!current) break;
                    }
                    return level;
                }

                var $insertPos = $anchor;
                sorted.forEach(function(nodeData) {
                    var level = getLevel(nodeData);
                    var $node = createNode(nodeData, level);

                    // Set parent reference
                    if (nodeData.parent && nodeData.parent !== '#') {
                        $node.attr('data-parent-id', nodeData.parent);
                        // Children start hidden
                        $node.hide();
                    }

                    $insertPos.after($node);
                    $insertPos = $node;

                    // Handle pre-opened state
                    if (nodeData.state && nodeData.state.opened) {
                        $node.data('expanded', true);
                        $node.find('.tm-arrow').css('transform', 'rotate(90deg)');
                    }
                });

                // Show root level items
                $('.tm-node[data-level="0"]').show();

                // Mark all nodes with children in initialData as "loaded" since children are already in DOM
                $('.tm-node').each(function() {
                    var $n = $(this);
                    var nodeId = $n.attr('data-id');
                    // Check if this node has children in the DOM
                    var hasChildrenInDom = $('.tm-node[data-parent-id="' + nodeId + '"]').length > 0;
                    if (hasChildrenInDom) {
                        $n.data('loaded', true);
                    }
                });

                // Expand pre-opened nodes
                $('.tm-node').each(function() {
                    var $n = $(this);
                    if ($n.data('expanded')) {
                        showChildren($n);
                    }
                });

                // Hide the anchor element since tree nodes are now rendered
                $anchor.hide();
            }

            buildTree();
        });
    </script>

<#else>
    <#-- ========== LEGACY MODE ========== -->
    <li class="menu-item tree-menu-item">
        <#if label??>
            <a href="#" class="menu-link" onclick="return false;">
                <i class="fas fa-sitemap"></i>
                <span>${label}</span>
            </a>
        </#if>

        <div class="tree-menu-container" id="${treeId}" style="padding: 10px 15px;">
            <#if treeData?? && (treeData?size > 0)>
                <ul style="list-style: none; padding: 0; margin: 0;">
                    <#list treeData as node>
                        <li style="margin: 3px 0;">
                            <#if node.hasChildren>
                                <span class="tree-toggle" style="cursor: pointer; display: flex; align-items: center;" data-code="${node.code!""}">
                                    <i class="fa fa-plus-square-o" style="margin-right: 8px; color: #666;"></i>
                                    <i class="fa fa-folder-o" style="margin-right: 8px; color: #f0ad4e;"></i>
                                    <span>${node.label!""}</span>
                                </span>
                                <ul style="list-style: none; padding-left: 25px; display: none; margin-top: 3px;" id="children_${node.code!""}">
                                </ul>
                            <#else>
                                <a href="${node.url!""}" style="text-decoration: none; color: inherit; display: flex; align-items: center;">
                                    <i class="fa fa-file-o" style="margin-right: 8px; color: #5bc0de;"></i>
                                    <span>${node.label!""}</span>
                                </a>
                            </#if>
                        </li>
                    </#list>
                </ul>
            <#else>
                <div style="color: #999; padding: 10px; text-align: center;">
                    <i class="fas fa-info-circle"></i> No menu items available
                </div>
            </#if>
        </div>

        <script>
            jQuery(document).ready(function($) {
                var treeId = '${treeId}';
                var ajaxUrl = '${ajaxUrl!""}';

                $('#' + treeId).on('click', '.tree-toggle', function(e) {
                    e.preventDefault();
                    e.stopPropagation();

                    var $toggle = $(this);
                    var code = $toggle.data('code');
                    var $icon = $toggle.find('i:first');
                    var $childrenUl = $('#children_' + code);

                    if ($childrenUl.children().length === 0 && !$childrenUl.data('loaded')) {
                        $childrenUl.html('<li style="color: #666; font-style: italic; padding: 5px;"><i class="fa fa-spinner fa-spin"></i> Loading...</li>');
                        $childrenUl.slideDown(200);
                        $icon.removeClass('fa-plus-square-o').addClass('fa-minus-square-o');

                        $.ajax({
                            url: ajaxUrl,
                            type: 'GET',
                            data: { _ajaxTreeLoad: 'true', parentCode: code },
                            dataType: 'json',
                            success: function(data) {
                                $childrenUl.empty();
                                $childrenUl.data('loaded', true);

                                if (data && data.length > 0) {
                                    $.each(data, function(i, node) {
                                        var nodeHtml = '';
                                        if (node.children) {
                                            nodeHtml = '<li style="margin: 3px 0;"><span class="tree-toggle" style="cursor: pointer; display: flex; align-items: center;" data-code="' + (node.data ? node.data.code : '') + '"><i class="fa fa-plus-square-o" style="margin-right: 8px; color: #666;"></i><i class="fa fa-folder-o" style="margin-right: 8px; color: #f0ad4e;"></i><span>' + node.text + '</span></span><ul style="list-style: none; padding-left: 25px; display: none; margin-top: 3px;" id="children_' + (node.data ? node.data.code : '') + '"></ul></li>';
                                        } else {
                                            nodeHtml = '<li style="margin: 3px 0;"><a href="' + (node.data ? node.data.url : '#') + '" style="text-decoration: none; color: inherit; display: flex; align-items: center;"><i class="fa fa-file-o" style="margin-right: 8px; color: #5bc0de;"></i><span>' + node.text + '</span></a></li>';
                                        }
                                        $childrenUl.append(nodeHtml);
                                    });
                                } else {
                                    $childrenUl.html('<li style="color: #999; font-style: italic; padding: 5px;">No items</li>');
                                }
                            },
                            error: function() {
                                $childrenUl.html('<li style="color: #d9534f; padding: 5px;"><i class="fa fa-exclamation-triangle"></i> Error loading</li>');
                            }
                        });
                    } else {
                        if ($childrenUl.is(':visible')) {
                            $childrenUl.slideUp(200);
                            $icon.removeClass('fa-minus-square-o').addClass('fa-plus-square-o');
                        } else {
                            $childrenUl.slideDown(200);
                            $icon.removeClass('fa-plus-square-o').addClass('fa-minus-square-o');
                        }
                    }
                });
            });
        </script>
    </li>
</#if>
