/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.milo.opcua.sdk.server.nodes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.eclipse.milo.opcua.sdk.core.QualifiedProperty;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceManager;
import org.eclipse.milo.opcua.sdk.server.api.nodes.Node;
import org.eclipse.milo.opcua.sdk.server.api.nodes.ObjectNode;
import org.eclipse.milo.opcua.sdk.server.api.nodes.ObjectTypeNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NamingRuleType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;

import static org.eclipse.milo.opcua.sdk.core.Reference.HAS_COMPONENT_PREDICATE;
import static org.eclipse.milo.opcua.sdk.core.Reference.HAS_DESCRIPTION_PREDICATE;
import static org.eclipse.milo.opcua.sdk.core.Reference.HAS_EVENT_SOURCE_PREDICATE;
import static org.eclipse.milo.opcua.sdk.core.Reference.HAS_NOTIFIER_PREDICATE;
import static org.eclipse.milo.opcua.sdk.core.Reference.HAS_PROPERTY_PREDICATE;
import static org.eclipse.milo.opcua.sdk.core.Reference.HAS_TYPE_DEFINITION_PREDICATE;
import static org.eclipse.milo.opcua.sdk.core.Reference.ORGANIZES_PREDICATE;
import static org.eclipse.milo.opcua.sdk.core.util.StreamUtil.opt2stream;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;

public class UaObjectNode extends UaNode implements ObjectNode {

    private volatile UByte eventNotifier = ubyte(0);

    public UaObjectNode(
        UaNodeContext context,
        NodeId nodeId,
        ObjectTypeNode objectTypeNode) {

        this(context, nodeId, objectTypeNode.getBrowseName(), objectTypeNode.getDisplayName());

        setDescription(objectTypeNode.getDescription());
        setWriteMask(objectTypeNode.getWriteMask());
        setUserWriteMask(objectTypeNode.getUserWriteMask());
    }

    public UaObjectNode(
        UaNodeContext context,
        NodeId nodeId,
        QualifiedName browseName,
        LocalizedText displayName) {

        super(context, nodeId, NodeClass.Object, browseName, displayName);
    }

    public UaObjectNode(
        UaNodeContext context,
        NodeId nodeId,
        QualifiedName browseName,
        LocalizedText displayName,
        LocalizedText description,
        UInteger writeMask,
        UInteger userWriteMask) {

        super(context, nodeId, NodeClass.Object, browseName, displayName, description, writeMask, userWriteMask);
    }

    public UaObjectNode(
        UaNodeContext context,
        NodeId nodeId,
        QualifiedName browseName,
        LocalizedText displayName,
        LocalizedText description,
        UInteger writeMask,
        UInteger userWriteMask,
        UByte eventNotifier) {

        super(context, nodeId, NodeClass.Object,
            browseName, displayName, description, writeMask, userWriteMask);

        this.eventNotifier = eventNotifier;
    }

    @Override
    public UByte getEventNotifier() {
        return eventNotifier;
    }

    @Override
    public synchronized void setEventNotifier(UByte eventNotifier) {
        this.eventNotifier = eventNotifier;

        fireAttributeChanged(AttributeId.EventNotifier, eventNotifier);
    }

    @Nullable
    public UaMethodNode findMethodNode(NodeId methodId) {
        List<UaMethodNode> methodNodes = getMethodNodes();

        for (UaMethodNode methodNode : methodNodes) {
            if (methodId.equals(methodNode.getNodeId())) {
                return methodNode;
            }

            NodeId typeDefinitionId = getTypeDefinitionNode().getNodeId();

            NodeId methodDeclarationId = findMethodDeclarationId(
                typeDefinitionId,
                methodNode.getBrowseName()
            );

            if (methodId.equals(methodDeclarationId)) {
                return methodNode;
            }
        }

        return null;
    }

    /**
     * Find the NodeId of the MethodNode on the type definition with the same name as {@code methodName}.
     *
     * @param methodName the name of the MethodNode to search for.
     * @return the NodeId of the MethodNode on the type definition with the same name as {@code methodName}. Will be
     * {@link NodeId#NULL_VALUE} if not found.
     */
    private NodeId findMethodDeclarationId(NodeId typeDefinitionId, QualifiedName methodName) {
        AddressSpaceManager asm = getNodeContext()
            .getServer()
            .getAddressSpaceManager();

        NamespaceTable namespaceTable = getNodeContext()
            .getServer()
            .getNamespaceTable();

        NodeId nodeId = asm.getManagedReferences(typeDefinitionId)
            .stream()
            .filter(HAS_COMPONENT_PREDICATE)
            .flatMap(r -> opt2stream(getManagedNode(r.getTargetNodeId())))
            .filter(n ->
                (n instanceof UaMethodNode) &&
                    Objects.equals(n.getBrowseName(), methodName))
            .findFirst()
            .map(Node::getNodeId)
            .orElse(NodeId.NULL_VALUE);

        if (nodeId.isNull()) {
            NodeId parentTypeId = asm.getManagedReferences(typeDefinitionId)
                .stream()
                .filter(Reference.SUBTYPE_OF)
                .flatMap(r -> opt2stream(r.getTargetNodeId().local(namespaceTable)))
                .findFirst()
                .orElse(null);

            if (parentTypeId != null) {
                return findMethodDeclarationId(parentTypeId, methodName);
            } else {
                return nodeId;
            }
        } else {
            return nodeId;
        }
    }

    public List<UaNode> getComponentNodes() {
        return getReferences().stream()
            .filter(HAS_COMPONENT_PREDICATE)
            .flatMap(r -> opt2stream(getManagedNode(r.getTargetNodeId())))
            .collect(Collectors.toList());
    }

    public List<UaNode> getPropertyNodes() {
        return getReferences().stream()
            .filter(HAS_PROPERTY_PREDICATE)
            .flatMap(r -> opt2stream(getManagedNode(r.getTargetNodeId())))
            .collect(Collectors.toList());
    }

    public List<UaMethodNode> getMethodNodes() {
        return getReferences().stream()
            .filter(HAS_COMPONENT_PREDICATE)
            .flatMap(r -> opt2stream(getManagedNode(r.getTargetNodeId())))
            .filter(n -> (n instanceof UaMethodNode))
            .map(UaMethodNode.class::cast)
            .collect(Collectors.toList());
    }

    public ObjectTypeNode getTypeDefinitionNode() {
        Node node = getReferences().stream()
            .filter(HAS_TYPE_DEFINITION_PREDICATE)
            .findFirst()
            .flatMap(r -> getManagedNode(r.getTargetNodeId()))
            .orElse(null);

        return (node instanceof ObjectTypeNode) ? (ObjectTypeNode) node : null;
    }

    public List<Node> getEventSourceNodes() {
        return getReferences().stream()
            .filter(HAS_EVENT_SOURCE_PREDICATE)
            .flatMap(r -> opt2stream(getManagedNode(r.getTargetNodeId())))
            .collect(Collectors.toList());
    }

    public List<Node> getNotifierNodes() {
        return getReferences().stream()
            .filter(HAS_NOTIFIER_PREDICATE)
            .flatMap(r -> opt2stream(getManagedNode(r.getTargetNodeId())))
            .collect(Collectors.toList());
    }

    public List<Node> getOrganizesNodes() {
        return getReferences().stream()
            .filter(ORGANIZES_PREDICATE)
            .flatMap(r -> opt2stream(getManagedNode(r.getTargetNodeId())))
            .collect(Collectors.toList());
    }

    public Optional<Node> getDescriptionNode() {
        Optional<UaNode> node = getReferences().stream()
            .filter(HAS_DESCRIPTION_PREDICATE)
            .findFirst()
            .flatMap(r -> getManagedNode(r.getTargetNodeId()));

        return node.map(n -> n);
    }

    /**
     * Add a 'HasComponent' reference from this Object to {@code node} and an inverse 'ComponentOf' reference from
     * {@code node} back to this Object.
     *
     * @param node the node to add as a component of this Object.
     */
    public void addComponent(UaNode node) {
        addReference(new Reference(
            getNodeId(),
            Identifiers.HasComponent,
            node.getNodeId().expanded(),
            true
        ));
    }

    /**
     * Remove the 'HasComponent' reference from this Object to {@code node} and the inverse 'ComponentOf' reference
     * from {@code node} back to this Object.
     *
     * @param node the node to remove as a component of this Object.
     */
    public void removeComponent(UaNode node) {
        removeReference(new Reference(
            getNodeId(),
            Identifiers.HasComponent,
            node.getNodeId().expanded(),
            true
        ));
    }

    @Nullable
    public String getNodeVersion() {
        return getProperty(NodeVersion).orElse(null);
    }

    @Nullable
    public ByteString getIcon() {
        return getProperty(Icon).orElse(null);
    }

    @Nullable
    public NamingRuleType getNamingRule() {
        return getProperty(NamingRule).orElse(null);
    }

    public void setNodeVersion(String nodeVersion) {
        setProperty(NodeVersion, nodeVersion);
    }

    public void setIcon(ByteString icon) {
        setProperty(Icon, icon);
    }

    public void setNamingRule(NamingRuleType namingRule) {
        setProperty(NamingRule, namingRule);
    }

    public static final QualifiedProperty<String> NodeVersion = new QualifiedProperty<>(
        Namespaces.OPC_UA,
        "NodeVersion",
        Identifiers.String,
        ValueRanks.Scalar,
        String.class
    );

    public static final QualifiedProperty<ByteString> Icon = new QualifiedProperty<>(
        Namespaces.OPC_UA,
        "Icon",
        Identifiers.Image,
        ValueRanks.Scalar,
        ByteString.class
    );

    public static final QualifiedProperty<NamingRuleType> NamingRule = new QualifiedProperty<>(
        Namespaces.OPC_UA,
        "NamingRule",
        Identifiers.NamingRuleType,
        ValueRanks.Scalar,
        NamingRuleType.class
    );

    public static UaObjectNodeBuilder builder(UaNodeContext context) {
        return new UaObjectNodeBuilder(context);
    }

    public static class UaObjectNodeBuilder implements Supplier<UaObjectNode> {

        private final List<Reference> references = Lists.newArrayList();

        private NodeId nodeId;
        private QualifiedName browseName;
        private LocalizedText displayName;
        private LocalizedText description = LocalizedText.NULL_VALUE;
        private UInteger writeMask = UInteger.MIN;
        private UInteger userWriteMask = UInteger.MIN;
        private UByte eventNotifier = ubyte(0);

        private final UaNodeContext context;

        public UaObjectNodeBuilder(UaNodeContext context) {
            this.context = context;
        }

        @Override
        public UaObjectNode get() {
            return build();
        }

        /**
         * Builds the configured {@link UaObjectNode}.
         * <p>
         * The following fields are required: NodeId, NodeClass, BrowseName, DisplayName.
         * <p>
         * Exactly one HasTypeDefinition reference must be present.
         *
         * @return a {@link UaObjectNode}.
         * @throws NullPointerException  if any of the required fields are null.
         * @throws IllegalStateException if exactly one HasTypeDefinition reference is not present.
         */
        public UaObjectNode build() {
            Preconditions.checkNotNull(nodeId, "NodeId cannot be null");
            Preconditions.checkNotNull(browseName, "BrowseName cannot be null");
            Preconditions.checkNotNull(displayName, "DisplayName cannot be null");

            long hasTypeDefinitionCount = references.stream()
                .filter(r -> Identifiers.HasTypeDefinition.equals(r.getReferenceTypeId())).count();

            Preconditions.checkState(
                hasTypeDefinitionCount == 1,
                "Object Node must have exactly one HasTypeDefinition reference.");

            // TODO More validation on references.

            UaObjectNode node = new UaObjectNode(
                context,
                nodeId,
                browseName,
                displayName,
                description,
                writeMask,
                userWriteMask,
                eventNotifier
            );

            references.forEach(node::addReference);

            return node;
        }

        public UaObjectNodeBuilder setNodeId(NodeId nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public UaObjectNodeBuilder setBrowseName(QualifiedName browseName) {
            this.browseName = browseName;
            return this;
        }

        public UaObjectNodeBuilder setDisplayName(LocalizedText displayName) {
            this.displayName = displayName;
            return this;
        }

        public UaObjectNodeBuilder setDescription(LocalizedText description) {
            this.description = description;
            return this;
        }

        public UaObjectNodeBuilder setWriteMask(UInteger writeMask) {
            this.writeMask = writeMask;
            return this;
        }

        public UaObjectNodeBuilder setUserWriteMask(UInteger userWriteMask) {
            this.userWriteMask = userWriteMask;
            return this;
        }

        public UaObjectNodeBuilder setEventNotifier(UByte eventNotifier) {
            this.eventNotifier = eventNotifier;
            return this;
        }

        public UaObjectNodeBuilder addReference(Reference reference) {
            references.add(reference);
            return this;
        }

        /**
         * Convenience method for adding the required HasTypeDefinition reference.
         * <p>
         * {@link #setNodeId(NodeId)} must have already been called before invoking this method.
         *
         * @param typeDefinition The {@link NodeId} of the TypeDefinition.
         * @return this {@link UaObjectNodeBuilder}.
         */
        public UaObjectNodeBuilder setTypeDefinition(NodeId typeDefinition) {
            Objects.requireNonNull(nodeId, "NodeId cannot be null");

            references.add(new Reference(
                nodeId,
                Identifiers.HasTypeDefinition,
                new ExpandedNodeId(typeDefinition),
                true
            ));

            return this;
        }

    }

}
