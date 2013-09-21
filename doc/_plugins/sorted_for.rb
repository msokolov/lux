module Jekyll
  class SortedForTag < Liquid::For
    def render(context)
      sorted_collection = context[@collection_name].dup
      sort_attr = @attributes['sort_by']

      sorted_collection.sort_by! { |i| i.to_liquid[sort_attr] }

      sorted_collection_name = "#{@collection_name}_sorted".sub('.', '_')
      context[sorted_collection_name] = sorted_collection
      @collection_name = sorted_collection_name
 
      super
    end
 
    def end_tag
      'endsorted_for'
    end
  end
end
 
Liquid::Template.register_tag('sorted_for', Jekyll::SortedForTag)
